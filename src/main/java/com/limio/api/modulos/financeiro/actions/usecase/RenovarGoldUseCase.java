package com.limio.api.modulos.financeiro.actions.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.financeiro.actions.helper.ResolverCobrancaGoldHelper;
import com.limio.api.modulos.financeiro.actions.service.GatewayPagamento;
import com.limio.api.modulos.financeiro.actions.service.ResultadoCobranca;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.Cobranca;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.CobrancaService;
import com.limio.api.modulos.financeiro.excecao.GatewayIndisponivelException;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamento;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamentoService;
import com.limio.api.modulos.financeiro.plano.Plano;
import com.limio.api.modulos.financeiro.plano.PlanoService;

/**
 * Rotina periódica da renovação mensal do GOLD (ETI-85, UC "Falha de cobrança e retry" e fim do ciclo do UC "Cancelar
 * assinatura GOLD"). Para cada assinatura {@code ATIVA} com o ciclo vencido e sem nova tentativa marcada para depois:
 *
 * <ul>
 *   <li>renovação cancelada pelo Prestador (US27): encerra a assinatura, que volta para FREE;</li>
 *   <li>senão, cobra o próximo ciclo no cartão salvo. O resultado é aplicado por {@link ResolverCobrancaGoldHelper}:
 *   aprovada avança o ciclo; recusada agenda a próxima tentativa ou, esgotadas, encerra; sem resposta do gateway,
 *   tenta de novo depois. Sem cartão salvo conta como recusada, sem chamar o gateway.</li>
 * </ul>
 *
 * <p>A "fila" das novas tentativas é a própria assinatura: {@code proximaTentativaEm} diz quando ela volta a ser
 * pré-selecionada. Enquanto as tentativas não acabam, a assinatura segue {@code ATIVA} e o Prestador segue GOLD.
 *
 * <p>Mesmo desenho de três passos de {@link AssinarGoldUseCase}: reserva numa transação curta, gateway fora de
 * transação, confirmação numa segunda transação curta. As duas transações tomam a trava {@code ASSINATURA_GOLD} do
 * usuário (a mesma de assinar e do aviso do gateway) e releem o estado depois dela; a confirmação não faz nada se o
 * aviso do gateway já resolveu a cobrança nesse meio tempo.
 *
 * <p>Uma cobrança anterior ainda {@code PENDENTE} (o processo caiu entre cobrar e confirmar) espera o aviso do
 * gateway por {@code espera-aviso-gateway}; depois disso vira {@code FALHOU} e a renovação segue.
 *
 * <p>{@link #executar()} não é transacional: falha numa assinatura é logada sem impedir as outras. Processa até
 * {@code lote} por execução; sobrando, a execução seguinte continua. Sem ShedLock: a trava e a releitura bastam com
 * várias instâncias (mesmo padrão de {@code ExpirarReagendamentosUseCase}).
 */
@Service
public class RenovarGoldUseCase {

    private static final Logger log = LoggerFactory.getLogger(RenovarGoldUseCase.class);

    private final AssinaturaGoldService assinaturaGoldService;
    private final CobrancaService cobrancaService;
    private final PlanoService planoService;
    private final MeioPagamentoService meioPagamentoService;
    private final GatewayPagamento gatewayPagamento;
    private final ResolverCobrancaGoldHelper resolverCobranca;
    private final TravaTransacional trava;
    private final TransactionOperations transacao;
    private final Clock relogio;
    private final int lote;
    private final Duration esperaAvisoGateway;

    public RenovarGoldUseCase(AssinaturaGoldService assinaturaGoldService, CobrancaService cobrancaService,
            PlanoService planoService, MeioPagamentoService meioPagamentoService, GatewayPagamento gatewayPagamento,
            ResolverCobrancaGoldHelper resolverCobranca, TravaTransacional trava, TransactionOperations transacao,
            Clock relogio, @Value("${financeiro.gold.renovacao.lote}") int lote,
            @Value("${financeiro.gold.renovacao.espera-aviso-gateway}") Duration esperaAvisoGateway) {
        this.assinaturaGoldService = assinaturaGoldService;
        this.cobrancaService = cobrancaService;
        this.planoService = planoService;
        this.meioPagamentoService = meioPagamentoService;
        this.gatewayPagamento = gatewayPagamento;
        this.resolverCobranca = resolverCobranca;
        this.trava = trava;
        this.transacao = transacao;
        this.relogio = relogio;
        this.lote = lote;
        this.esperaAvisoGateway = esperaAvisoGateway;
    }

    @Scheduled(cron = "${financeiro.gold.renovacao.cron}")
    public void executar() {
        Instant agora = relogio.instant();
        List<UUID> candidatas = assinaturaGoldService.buscarIdsParaRenovar(agora, lote);

        for (UUID assinaturaId : candidatas) {
            try {
                renovarUma(assinaturaId, agora);
            } catch (RuntimeException e) {
                log.error("falha ao renovar a assinatura GOLD {}; as demais continuam", assinaturaId, e);
            }
        }

        if (!candidatas.isEmpty()) {
            log.info("renovação do GOLD: {} assinaturas vencidas processadas", candidatas.size());
        }
    }

    private void renovarUma(UUID assinaturaId, Instant agora) {
        Optional<Reserva> reserva = transacao.execute(status -> prepararRenovacao(assinaturaId, agora));
        if (reserva == null || reserva.isEmpty()) {
            return;
        }

        ResultadoCobranca resultado;
        try {
            resultado = cobrar(reserva.get());
        } catch (GatewayIndisponivelException e) {
            log.warn("gateway sem resposta na renovação da assinatura GOLD {}; nova tentativa agendada", assinaturaId);
            transacao.executeWithoutResult(status -> registrarFalha(reserva.get(), agora));
            return;
        }
        transacao.executeWithoutResult(status -> confirmar(reserva.get(), resultado, agora));
    }

    /**
     * Passo 1, sob a trava: decide o que fazer com a assinatura vencida. Renovação cancelada encerra; cobrança anterior
     * ainda sem resposta adia (ou, passada a espera, é dada como falha); senão reserva a cobrança do próximo ciclo.
     * Vazio quando não há nada para cobrar agora.
     */
    private Optional<Reserva> prepararRenovacao(UUID assinaturaId, Instant agora) {
        Optional<UUID> usuarioId = assinaturaGoldService.buscarUsuarioId(assinaturaId);
        if (usuarioId.isEmpty()) {
            return Optional.empty();
        }
        trava.travar(EscopoTrava.ASSINATURA_GOLD, usuarioId.get().toString());

        Optional<AssinaturaGold> vencida = assinaturaGoldService.buscarPorId(assinaturaId)
                .filter(assinatura -> assinatura.renovacaoVencida(agora));
        if (vencida.isEmpty()) {
            return Optional.empty();
        }
        AssinaturaGold assinatura = vencida.get();

        if (assinatura.renovacaoCancelada()) {
            encerrarNoFimDoCiclo(assinatura, agora);
            return Optional.empty();
        }

        Optional<Cobranca> pendente = cobrancaService.buscarPendente(assinaturaId);
        if (pendente.isPresent()) {
            Instant esperaAte = pendente.get().getCriadoEm().plus(esperaAvisoGateway);
            if (esperaAte.isAfter(agora)) {
                adiarAteOAvisoDoGateway(assinatura, esperaAte);
                return Optional.empty();
            }
            desistirDaCobrancaSemResposta(pendente.get());
        }

        return Optional.of(reservarCobranca(assinatura, usuarioId.get()));
    }

    private void encerrarNoFimDoCiclo(AssinaturaGold assinatura, Instant agora) {
        assinatura.encerrar(agora);
        assinaturaGoldService.salvar(assinatura);
        log.info("assinatura GOLD {} encerrada no fim do ciclo: renovação cancelada", assinatura.getId());
    }

    /** A cobrança anterior ainda pode ter sido feita: não cobra de novo o mesmo ciclo antes do aviso do gateway. */
    private void adiarAteOAvisoDoGateway(AssinaturaGold assinatura, Instant esperaAte) {
        assinatura.agendarNovaTentativa(esperaAte);
        assinaturaGoldService.salvar(assinatura);
    }

    private void desistirDaCobrancaSemResposta(Cobranca pendente) {
        pendente.marcarFalha();
        cobrancaService.salvar(pendente);
    }

    private Reserva reservarCobranca(AssinaturaGold assinatura, UUID usuarioId) {
        Plano plano = planoService.buscarPorIdOuFalhar(assinatura.getPlanoId());
        int tentativa = cobrancaService.contarRecusadasDoCiclo(assinatura.getId(), assinatura.getDataFimCiclo()) + 1;
        String chaveIdempotencia = UUID.randomUUID().toString();
        Cobranca cobranca = cobrancaService.salvar(Cobranca.renovacao(assinatura.getId(), plano.getPrecoCentavos(),
                chaveIdempotencia, assinatura.getDataFimCiclo(), tentativa));
        String gatewayToken = meioPagamentoService.buscarPorUsuario(usuarioId)
                .map(MeioPagamento::getGatewayToken)
                .orElse(null);

        return new Reserva(assinatura.getId(), cobranca.getId(), usuarioId, gatewayToken, plano.getPrecoCentavos(),
                plano.getMoeda(), chaveIdempotencia);
    }

    private ResultadoCobranca cobrar(Reserva reserva) {
        if (reserva.gatewayToken() == null) {
            log.info("assinatura GOLD {} sem cartão salvo: renovação recusada sem chamar o gateway",
                    reserva.assinaturaId());
            return ResultadoCobranca.recusada();
        }
        return gatewayPagamento.cobrar(reserva.usuarioId(), reserva.gatewayToken(), reserva.valorCentavos(),
                reserva.moeda(), reserva.chaveIdempotencia());
    }

    private void confirmar(Reserva reserva, ResultadoCobranca resultado, Instant agora) {
        carregarSemResultado(reserva).ifPresent(carregada -> {
            if (resultado.aprovado()) {
                resolverCobranca.aprovar(carregada.assinatura(), carregada.cobranca(), resultado.gatewayCobrancaId(),
                        agora);
            } else {
                resolverCobranca.recusar(carregada.assinatura(), carregada.cobranca(), agora);
            }
        });
    }

    private void registrarFalha(Reserva reserva, Instant agora) {
        carregarSemResultado(reserva).ifPresent(
                carregada -> resolverCobranca.registrarFalha(carregada.assinatura(), carregada.cobranca(), agora));
    }

    /** Assinatura e cobrança lidas sob a trava; vazio se o aviso do gateway já resolveu a cobrança. */
    private Optional<Carregada> carregarSemResultado(Reserva reserva) {
        trava.travar(EscopoTrava.ASSINATURA_GOLD, reserva.usuarioId().toString());
        Cobranca cobranca = cobrancaService.buscarPorIdOuFalhar(reserva.cobrancaId());
        if (!cobranca.aguardaResultado()) {
            return Optional.empty();
        }
        return Optional.of(new Carregada(assinaturaGoldService.buscarPorIdOuFalhar(reserva.assinaturaId()), cobranca));
    }

    /** O que o passo 1 deixa para o gateway (passo 2) e a confirmação (passo 3). */
    private record Reserva(UUID assinaturaId, UUID cobrancaId, UUID usuarioId, String gatewayToken, long valorCentavos,
            String moeda, String chaveIdempotencia) {
    }

    private record Carregada(AssinaturaGold assinatura, Cobranca cobranca) {
    }
}
