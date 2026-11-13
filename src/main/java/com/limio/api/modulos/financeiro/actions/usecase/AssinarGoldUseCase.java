package com.limio.api.modulos.financeiro.actions.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.financeiro.actions.helper.ExigirPrestadorHelper;
import com.limio.api.modulos.financeiro.actions.service.GatewayPagamento;
import com.limio.api.modulos.financeiro.actions.service.ResultadoCobranca;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.Cobranca;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.CobrancaService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal.DocumentoFiscal;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal.DocumentoFiscalService;
import com.limio.api.modulos.financeiro.eventos.GoldAssinadoEvent;
import com.limio.api.modulos.financeiro.excecao.AssinaturaJaAtivaException;
import com.limio.api.modulos.financeiro.excecao.CobrancaEmAndamentoException;
import com.limio.api.modulos.financeiro.excecao.PagamentoRecusadoException;
import com.limio.api.modulos.financeiro.excecao.PlanoNaoEncontradoException;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamentoService;
import com.limio.api.modulos.financeiro.plano.Plano;
import com.limio.api.modulos.financeiro.plano.PlanoService;

/**
 * UC — Prestador assina o plano GOLD (ETI-81): cobra no gateway e, se aprovado, grava
 * {@code AssinaturaGold} + {@code Cobranca} + {@code DocumentoFiscal} e publica
 * {@link GoldAssinadoEvent} pro recibo. Recusado: a assinatura fica {@code CANCELADA} (nunca chega a
 * {@code ATIVA}) e o Prestador permanece FREE.
 *
 * <p>A chamada ao gateway é um efeito externo irreversível (timeout de até alguns segundos) e por isso
 * nunca roda dentro de transação — segurar o advisory lock e uma conexão do pool durante uma chamada HTTP
 * teria o mesmo problema que o {@code PublicarAnuncioUseCase} já evita. O fluxo é em três passos:
 *
 * <ol>
 *   <li>{@link #reservar} — transação curta: trava {@link EscopoTrava#ASSINATURA_GOLD} (clique duplo
 *   concorrente entra em fila), valida plano e estado atual, e grava {@code AssinaturaGold} +
 *   {@code Cobranca} ambas {@code PENDENTE}. O índice único em {@code usuario_id} é quem protege contra
 *   uma segunda tentativa depois que esta transação já comitou (e soltou o lock): o segundo clique
 *   encontra a linha {@code PENDENTE} e é recusado por {@link CobrancaEmAndamentoException}, sem chegar a
 *   chamar o gateway de novo;</li>
 *   <li>chamada ao {@link GatewayPagamento}, fora de qualquer transação;</li>
 *   <li>{@link #confirmar} — segunda transação curta: aprovado, ativa a assinatura, grava o recibo e
 *   publica o evento; recusado, cancela a pendência.</li>
 * </ol>
 *
 * <p>Uma falha entre o passo 2 e o 3 (o processo cai, por exemplo) deixa {@code AssinaturaGold} e
 * {@code Cobranca} em {@code PENDENTE} — pendência conhecida, sem reconciliação automática ainda (o
 * webhook do Asaas só loga hoje). Até reconciliar manualmente, novas tentativas do mesmo usuário recebem
 * {@link CobrancaEmAndamentoException}.
 */
@Service
public class AssinarGoldUseCase {

    private final PlanoService planoService;
    private final AssinaturaGoldService assinaturaGoldService;
    private final MeioPagamentoService meioPagamentoService;
    private final CobrancaService cobrancaService;
    private final DocumentoFiscalService documentoFiscalService;
    private final GatewayPagamento gatewayPagamento;
    private final TravaTransacional travaTransacional;
    private final ApplicationEventPublisher eventos;
    private final TransactionOperations transacao;
    private final Clock relogio;

    public AssinarGoldUseCase(PlanoService planoService,
            AssinaturaGoldService assinaturaGoldService,
            MeioPagamentoService meioPagamentoService,
            CobrancaService cobrancaService,
            DocumentoFiscalService documentoFiscalService,
            GatewayPagamento gatewayPagamento,
            TravaTransacional travaTransacional,
            ApplicationEventPublisher eventos,
            TransactionOperations transacao,
            Clock relogio) {
        this.planoService = planoService;
        this.assinaturaGoldService = assinaturaGoldService;
        this.meioPagamentoService = meioPagamentoService;
        this.cobrancaService = cobrancaService;
        this.documentoFiscalService = documentoFiscalService;
        this.gatewayPagamento = gatewayPagamento;
        this.travaTransacional = travaTransacional;
        this.eventos = eventos;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    public AssinaturaGold executar(UUID usuarioId, PapelUsuario papelAtivo, String codigoPlano, String gatewayToken) {
        ExigirPrestadorHelper.exigirPrestador(papelAtivo);

        Reserva reserva = transacao.execute(status -> reservar(usuarioId, codigoPlano));

        ResultadoCobranca resultado = gatewayPagamento.cobrar(usuarioId, gatewayToken, reserva.valorCentavos(),
                reserva.moeda(), reserva.chaveIdempotencia());

        return transacao.execute(status -> confirmar(usuarioId, gatewayToken, reserva, resultado));
    }

    private Reserva reservar(UUID usuarioId, String codigoPlano) {
        travaTransacional.travar(EscopoTrava.ASSINATURA_GOLD, usuarioId.toString());

        Plano plano = planoService.buscarAtivoPorCodigo(codigoPlano).orElseThrow(PlanoNaoEncontradoException::new);
        Optional<AssinaturaGold> existente = assinaturaGoldService.buscarPorUsuario(usuarioId);
        existente.ifPresent(AssinarGoldUseCase::exigirSemCobrancaEmAberto);

        Instant inicio = relogio.instant();
        Instant fim = plano.fimDoCiclo(inicio);
        AssinaturaGold assinatura = existente
                .map(a -> {
                    a.reiniciarPendencia(plano.getId(), inicio, fim);
                    return a;
                })
                .orElseGet(() -> AssinaturaGold.nova(usuarioId, plano.getId(), inicio, fim));
        assinatura = assinaturaGoldService.salvar(assinatura);

        String chaveIdempotencia = UUID.randomUUID().toString();
        Cobranca cobranca = cobrancaService
                .salvar(Cobranca.pendente(assinatura.getId(), plano.getPrecoCentavos(), chaveIdempotencia));

        return new Reserva(assinatura.getId(), cobranca.getId(), plano.getNome(), plano.getPrecoCentavos(),
                plano.getMoeda(), fim, chaveIdempotencia);
    }

    private AssinaturaGold confirmar(UUID usuarioId, String gatewayToken, Reserva reserva,
            ResultadoCobranca resultado) {
        AssinaturaGold assinatura = assinaturaGoldService.buscarPorIdOuFalhar(reserva.assinaturaId());
        Cobranca cobranca = cobrancaService.buscarPorIdOuFalhar(reserva.cobrancaId());

        if (!resultado.aprovado()) {
            assinatura.cancelarPendencia();
            assinaturaGoldService.salvar(assinatura);
            cobranca.confirmarRecusada();
            cobrancaService.salvar(cobranca);
            throw new PagamentoRecusadoException();
        }

        meioPagamentoService.salvarOuSubstituir(usuarioId, gatewayToken, resultado.bandeira(), resultado.ultimos4());

        assinatura.confirmarAtiva();
        assinatura = assinaturaGoldService.salvar(assinatura);

        cobranca.confirmarAprovada(resultado.gatewayCobrancaId());
        cobranca = cobrancaService.salvar(cobranca);
        DocumentoFiscal recibo = documentoFiscalService.salvar(DocumentoFiscal.emitir(cobranca, relogio.instant()));

        eventos.publishEvent(new GoldAssinadoEvent(usuarioId, reserva.planoNome(), reserva.valorCentavos(),
                reserva.moeda(), reserva.fimCiclo(), recibo.getNumero()));
        return assinatura;
    }

    private static void exigirSemCobrancaEmAberto(AssinaturaGold existente) {
        if (existente.estaAtiva()) {
            throw new AssinaturaJaAtivaException();
        }
        if (existente.estaPendente()) {
            throw new CobrancaEmAndamentoException();
        }
    }

    /** Dados da reserva (passo 1) que o passo 2 (gateway) e o passo 3 (confirmação) precisam. */
    private record Reserva(UUID assinaturaId, UUID cobrancaId, String planoNome, long valorCentavos, String moeda,
            Instant fimCiclo, String chaveIdempotencia) {
    }
}
