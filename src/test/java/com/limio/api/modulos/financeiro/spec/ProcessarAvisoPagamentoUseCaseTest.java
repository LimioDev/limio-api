package com.limio.api.modulos.financeiro.spec;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.financeiro.actions.helper.ResolverCobrancaGoldHelper;
import com.limio.api.modulos.financeiro.actions.usecase.ProcessarAvisoPagamentoUseCase;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.AvisoPagamento;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.Cobranca;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.CobrancaService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums.ResultadoAvisoPagamento;

@ExtendWith(MockitoExtension.class)
class ProcessarAvisoPagamentoUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-26T12:15:00Z");
    private static final UUID COBRANCA_ID = UUID.randomUUID();
    private static final UUID ASSINATURA_ID = UUID.randomUUID();
    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private CobrancaService cobrancaService;

    @Mock
    private AssinaturaGoldService assinaturaGoldService;

    @Mock
    private ResolverCobrancaGoldHelper resolverCobranca;

    @Mock
    private TravaTransacional trava;

    private ProcessarAvisoPagamentoUseCase useCase;
    private final AssinaturaGold assinatura = AssinaturaGold.nova(USUARIO_ID, UUID.randomUUID(), AGORA, AGORA);
    private final Cobranca cobranca = Cobranca.renovacao(ASSINATURA_ID, 2990, "chave", AGORA, 1);

    @BeforeEach
    void montar() {
        useCase = new ProcessarAvisoPagamentoUseCase(cobrancaService, assinaturaGoldService, resolverCobranca, trava,
                TransactionOperations.withoutTransaction(), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void acharACobranca() {
        when(cobrancaService.buscarIdDoAviso("chave", "pay_1")).thenReturn(Optional.of(COBRANCA_ID));
        when(cobrancaService.buscarAssinaturaId(COBRANCA_ID)).thenReturn(Optional.of(ASSINATURA_ID));
        when(assinaturaGoldService.buscarUsuarioId(ASSINATURA_ID)).thenReturn(Optional.of(USUARIO_ID));
        when(cobrancaService.buscarPorIdOuFalhar(COBRANCA_ID)).thenReturn(cobranca);
    }

    @Test
    void dadoAvisoDeAprovacao_quandoExecuta_entaoAprovaSobATrava() {
        acharACobranca();
        when(assinaturaGoldService.buscarPorIdOuFalhar(ASSINATURA_ID)).thenReturn(assinatura);

        useCase.executar(new AvisoPagamento(ResultadoAvisoPagamento.APROVADO, "chave", "pay_1"));

        verify(trava).travar(EscopoTrava.ASSINATURA_GOLD, USUARIO_ID.toString());
        verify(resolverCobranca).aprovar(assinatura, cobranca, "pay_1", AGORA);
    }

    @Test
    void dadoAvisoDeRecusa_quandoExecuta_entaoRecusa() {
        acharACobranca();
        when(assinaturaGoldService.buscarPorIdOuFalhar(ASSINATURA_ID)).thenReturn(assinatura);

        useCase.executar(new AvisoPagamento(ResultadoAvisoPagamento.RECUSADO, "chave", "pay_1"));

        verify(resolverCobranca).recusar(assinatura, cobranca, AGORA);
    }

    @Test
    void dadoCobrancaJaResolvida_quandoExecuta_entaoIgnoraOAvisoRepetido() {
        acharACobranca();
        cobranca.confirmarAprovada("pay_1");

        useCase.executar(new AvisoPagamento(ResultadoAvisoPagamento.APROVADO, "chave", "pay_1"));

        verify(resolverCobranca, never()).aprovar(any(), any(), any(), any());
        verify(assinaturaGoldService, never()).buscarPorIdOuFalhar(any());
    }

    @Test
    void dadoCobrancaQueNaoEDoLimio_quandoExecuta_entaoSoLoga() {
        when(cobrancaService.buscarIdDoAviso("outra", "pay_x")).thenReturn(Optional.empty());

        useCase.executar(new AvisoPagamento(ResultadoAvisoPagamento.APROVADO, "outra", "pay_x"));

        verifyNoInteractions(trava, resolverCobranca);
    }
}
