package com.limio.api.modulos.financeiro.actions.helper;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.limio.api.comum.eventos.CobrancaGoldAprovadaEvent;
import com.limio.api.comum.eventos.CobrancaGoldRecusadaEvent;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.Cobranca;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.CobrancaService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal.DocumentoFiscal;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal.DocumentoFiscalService;
import com.limio.api.modulos.financeiro.eventos.GoldAssinadoEvent;
import com.limio.api.modulos.financeiro.plano.Plano;
import com.limio.api.modulos.financeiro.plano.PlanoService;

/**
 * O que acontece com a assinatura quando uma cobrança GOLD ganha resultado (ETI-85). Um lugar só para as duas origens
 * do resultado: a resposta síncrona do gateway na rotina de renovação ({@code RenovarGoldUseCase}) e o aviso que o
 * gateway manda depois pelo webhook ({@code ProcessarAvisoPagamentoUseCase}).
 *
 * <ul>
 *   <li><b>Aprovada</b>: renovação avança o ciclo, grava o recibo e publica {@link CobrancaGoldAprovadaEvent}.
 *   Cobrança da assinatura que ficou pendente (ETI-81) ativa o GOLD e publica {@link GoldAssinadoEvent}.</li>
 *   <li><b>Recusada</b>: renovação agenda a próxima tentativa daqui a {@code intervalo-tentativa}, ou encerra a
 *   assinatura quando as tentativas do ciclo chegam a {@code max-tentativas}, e publica {@link
 *   CobrancaGoldRecusadaEvent}. Cobrança da assinatura pendente cancela a pendência.</li>
 *   <li><b>Falha do gateway</b> (sem resposta): só agenda a próxima tentativa. Não conta como recusa nem avisa.</li>
 * </ul>
 *
 * <p>Um resultado de um ciclo que já não está em aberto (renovado ou encerrado por outra via) só fica registrado na
 * cobrança, sem mexer na assinatura. Se foi uma aprovação, o Prestador pagou duas vezes, e o log pede conferência.
 *
 * <p>Precisa rodar dentro de transação, com a trava {@code ASSINATURA_GOLD} do usuário já tomada por quem chama, e com
 * a assinatura e a cobrança lidas depois da trava.
 */
@Component
public class ResolverCobrancaGoldHelper {

    private static final Logger log = LoggerFactory.getLogger(ResolverCobrancaGoldHelper.class);

    private final AssinaturaGoldService assinaturaGoldService;
    private final CobrancaService cobrancaService;
    private final DocumentoFiscalService documentoFiscalService;
    private final PlanoService planoService;
    private final ApplicationEventPublisher eventos;
    private final int maxTentativas;
    private final Duration intervaloTentativa;

    public ResolverCobrancaGoldHelper(AssinaturaGoldService assinaturaGoldService, CobrancaService cobrancaService,
            DocumentoFiscalService documentoFiscalService, PlanoService planoService,
            ApplicationEventPublisher eventos,
            @Value("${financeiro.gold.renovacao.max-tentativas}") int maxTentativas,
            @Value("${financeiro.gold.renovacao.intervalo-tentativa}") Duration intervaloTentativa) {
        if (maxTentativas < 1) {
            throw new IllegalArgumentException("financeiro.gold.renovacao.max-tentativas precisa ser pelo menos 1");
        }
        this.assinaturaGoldService = assinaturaGoldService;
        this.cobrancaService = cobrancaService;
        this.documentoFiscalService = documentoFiscalService;
        this.planoService = planoService;
        this.eventos = eventos;
        this.maxTentativas = maxTentativas;
        this.intervaloTentativa = intervaloTentativa;
    }

    /** Quando a próxima tentativa sai, contando de {@code agora}. */
    private Instant proximaTentativa(Instant agora) {
        return agora.plus(intervaloTentativa);
    }

    public void aprovar(AssinaturaGold assinatura, Cobranca cobranca, String gatewayCobrancaId, Instant agora) {
        cobranca.confirmarAprovada(gatewayCobrancaId == null ? cobranca.getGatewayCobrancaId() : gatewayCobrancaId);
        cobrancaService.salvar(cobranca);

        if (cobranca.ehRenovacao()) {
            aprovarRenovacao(assinatura, cobranca, agora);
        } else {
            aprovarAssinaturaPendente(assinatura, cobranca, agora);
        }
    }

    public void recusar(AssinaturaGold assinatura, Cobranca cobranca, Instant agora) {
        cobranca.confirmarRecusada();
        cobrancaService.salvar(cobranca);

        if (!cobranca.ehRenovacao()) {
            if (assinatura.estaPendente()) {
                assinatura.cancelarPendencia();
                assinaturaGoldService.salvar(assinatura);
            }
            return;
        }
        if (!assinatura.aguardaRenovacaoDe(cobranca.getInicioCicloCobrado())) {
            return;
        }

        int recusadas = cobrancaService.contarRecusadasDoCiclo(assinatura.getId(), cobranca.getInicioCicloCobrado());
        Instant proximaTentativa = null;
        if (recusadas >= maxTentativas) {
            assinatura.encerrar(agora);
            log.info("assinatura GOLD {} encerrada: {} cobranças da renovação recusadas", assinatura.getId(),
                    recusadas);
        } else {
            proximaTentativa = proximaTentativa(agora);
            assinatura.agendarNovaTentativa(proximaTentativa);
        }
        assinaturaGoldService.salvar(assinatura);
        eventos.publishEvent(new CobrancaGoldRecusadaEvent(assinatura.getUsuarioId(), recusadas, proximaTentativa,
                agora));
    }

    /** O gateway não respondeu na renovação: tenta de novo depois, sem contar recusa nem avisar o Prestador. */
    public void registrarFalha(AssinaturaGold assinatura, Cobranca cobranca, Instant agora) {
        cobranca.marcarFalha();
        cobrancaService.salvar(cobranca);
        assinatura.agendarNovaTentativa(proximaTentativa(agora));
        assinaturaGoldService.salvar(assinatura);
    }

    private void aprovarRenovacao(AssinaturaGold assinatura, Cobranca cobranca, Instant agora) {
        if (!assinatura.aguardaRenovacaoDe(cobranca.getInicioCicloCobrado())) {
            log.warn("cobrança de renovação {} aprovada fora do ciclo em aberto da assinatura {}: conferir estorno",
                    cobranca.getId(), assinatura.getId());
            return;
        }
        Plano plano = planoService.buscarPorIdOuFalhar(assinatura.getPlanoId());
        assinatura.renovar(plano.fimDoCiclo(assinatura.getDataFimCiclo()));
        assinaturaGoldService.salvar(assinatura);

        DocumentoFiscal recibo = documentoFiscalService.salvar(DocumentoFiscal.emitir(cobranca, agora));
        eventos.publishEvent(new CobrancaGoldAprovadaEvent(assinatura.getUsuarioId(), plano.getNome(),
                cobranca.getValorCentavos(), plano.getMoeda(), assinatura.getDataFimCiclo(), recibo.getNumero(),
                agora));
    }

    /**
     * Só pelo aviso do gateway: a cobrança da assinatura (ETI-81) ficou pendente porque a resposta síncrona não
     * chegou. O cartão não é salvo aqui, porque o aviso não traz o token.
     */
    private void aprovarAssinaturaPendente(AssinaturaGold assinatura, Cobranca cobranca, Instant agora) {
        if (!assinatura.estaPendente()) {
            log.warn("cobrança {} da assinatura aprovada com a assinatura {} em {}: conferir estorno",
                    cobranca.getId(), assinatura.getId(), assinatura.getStatus());
            return;
        }
        Plano plano = planoService.buscarPorIdOuFalhar(assinatura.getPlanoId());
        assinatura.confirmarAtiva();
        assinaturaGoldService.salvar(assinatura);

        DocumentoFiscal recibo = documentoFiscalService.salvar(DocumentoFiscal.emitir(cobranca, agora));
        eventos.publishEvent(new GoldAssinadoEvent(assinatura.getUsuarioId(), plano.getNome(),
                cobranca.getValorCentavos(), plano.getMoeda(), assinatura.getDataFimCiclo(), recibo.getNumero()));
    }
}
