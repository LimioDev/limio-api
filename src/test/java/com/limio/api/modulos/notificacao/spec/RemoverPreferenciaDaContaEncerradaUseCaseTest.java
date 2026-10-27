package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.eventos.ContaEncerradaEvent;
import com.limio.api.modulos.notificacao.actions.usecase.RemoverPreferenciaDaContaEncerradaUseCase;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacaoService;

@ExtendWith(MockitoExtension.class)
class RemoverPreferenciaDaContaEncerradaUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private PreferenciaNotificacaoService preferenciaNotificacaoService;

    @InjectMocks
    private RemoverPreferenciaDaContaEncerradaUseCase useCase;

    @Test
    void deveApagarAPreferenciaDoUsuario() {
        useCase.aoEncerrarConta(new ContaEncerradaEvent(USUARIO_ID, Instant.now()));

        verify(preferenciaNotificacaoService).apagarDoUsuario(USUARIO_ID);
    }

    @Test
    void falhaNaoVoltaParaQuemEncerrouAConta() {
        doThrow(new IllegalStateException("banco fora")).when(preferenciaNotificacaoService).apagarDoUsuario(USUARIO_ID);

        assertThatCode(() -> useCase.aoEncerrarConta(new ContaEncerradaEvent(USUARIO_ID, Instant.now())))
                .doesNotThrowAnyException();
    }
}
