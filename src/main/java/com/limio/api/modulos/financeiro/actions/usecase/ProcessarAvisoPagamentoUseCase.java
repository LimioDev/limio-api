package com.limio.api.modulos.financeiro.actions.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.financeiro.actions.helper.ResolverCobrancaGoldHelper;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.AvisoPagamento;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.Cobranca;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.CobrancaService;

/**
 * UC — receber o aviso do gateway sobre uma cobrança GOLD (ETI-85). O gateway avisa depois o resultado de uma
 * cobrança que o LIMIO ficou sem saber: a resposta síncrona se perdeu (o processo caiu, o gateway não respondeu a
 * tempo) ou o resultado só saiu depois (análise de risco). O efeito é o mesmo da resposta síncrona, pelo {@link
 * ResolverCobrancaGoldHelper}: renova, agenda nova tentativa, encerra, ou ativa/cancela a assinatura pendente.
 *
 * <p>Idempotente (UC "Webhook repetido"): a cobrança é achada pela {@code chaveIdempotencia} e só muda enquanto
 * estiver sem resultado ({@code PENDENTE} ou {@code FALHOU}). O mesmo aviso de novo, ou o aviso de uma cobrança que a
 * resposta síncrona já resolveu, encontra a cobrança resolvida e não grava nada. A decisão é tomada sob a trava {@code
 * ASSINATURA_GOLD} do usuário, a mesma da renovação e de assinar, com o estado lido depois dela.
 *
 * <p>Aviso de uma cobrança que não é do LIMIO só é logado.
 */
@Service
public class ProcessarAvisoPagamentoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarAvisoPagamentoUseCase.class);

    private final CobrancaService cobrancaService;
    private final AssinaturaGoldService assinaturaGoldService;
    private final ResolverCobrancaGoldHelper resolverCobranca;
    private final TravaTransacional trava;
    private final TransactionOperations transacao;
    private final Clock relogio;

    public ProcessarAvisoPagamentoUseCase(CobrancaService cobrancaService, AssinaturaGoldService assinaturaGoldService,
            ResolverCobrancaGoldHelper resolverCobranca, TravaTransacional trava, TransactionOperations transacao,
            Clock relogio) {
        this.cobrancaService = cobrancaService;
        this.assinaturaGoldService = assinaturaGoldService;
        this.resolverCobranca = resolverCobranca;
        this.trava = trava;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    public void executar(AvisoPagamento aviso) {
        Optional<UUID> cobrancaId = cobrancaService.buscarIdDoAviso(aviso.chaveIdempotencia(),
                aviso.gatewayCobrancaId());
        if (cobrancaId.isEmpty()) {
            log.info("aviso do gateway sem cobrança do LIMIO: chave={} gatewayCobrancaId={}",
                    aviso.chaveIdempotencia(), aviso.gatewayCobrancaId());
            return;
        }
        transacao.executeWithoutResult(status -> processar(cobrancaId.get(), aviso, relogio.instant()));
    }

    private void processar(UUID cobrancaId, AvisoPagamento aviso, Instant agora) {
        // Ids por consulta escalar e trava antes de ler a cobrança: senão a leitura seguinte devolveria a cópia de
        // antes da trava (mesmo cuidado de ExpirarReagendamentosUseCase).
        UUID assinaturaId = cobrancaService.buscarAssinaturaId(cobrancaId).orElseThrow();
        UUID usuarioId = assinaturaGoldService.buscarUsuarioId(assinaturaId).orElseThrow();
        trava.travar(EscopoTrava.ASSINATURA_GOLD, usuarioId.toString());

        Cobranca cobranca = cobrancaService.buscarPorIdOuFalhar(cobrancaId);
        if (!cobranca.aguardaResultado()) {
            log.info("aviso do gateway para a cobrança {} já resolvida ({}): ignorado", cobrancaId,
                    cobranca.getStatus());
            return;
        }
        AssinaturaGold assinatura = assinaturaGoldService.buscarPorIdOuFalhar(assinaturaId);
        switch (aviso.resultado()) {
            case APROVADO -> resolverCobranca.aprovar(assinatura, cobranca, aviso.gatewayCobrancaId(), agora);
            case RECUSADO -> resolverCobranca.recusar(assinatura, cobranca, agora);
        }
    }
}
