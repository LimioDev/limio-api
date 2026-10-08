package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.modulos.notificacao.actions.usecase.ConsultarPreferenciaNotificacaoUseCase;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacaoService;

@ExtendWith(MockitoExtension.class)
class ConsultarPreferenciaNotificacaoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private PreferenciaNotificacaoService preferenciaNotificacaoService;

    private ConsultarPreferenciaNotificacaoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsultarPreferenciaNotificacaoUseCase(preferenciaNotificacaoService);
    }

    @Test
    void deveDevolverPreferenciaSalvaDoUsuario() {
        PreferenciaNotificacao salva = PreferenciaNotificacao.builder()
                .usuarioId(USUARIO_ID).push(false).email(true).sms(true).whatsapp(false).build();
        when(preferenciaNotificacaoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(salva));

        PreferenciaNotificacao resultado = useCase.executar(USUARIO_ID);

        assertThat(resultado).isSameAs(salva);
    }

    @Test
    void semRegistroDeveDevolverPadroesDefinidosSemPersistir() {
        when(preferenciaNotificacaoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());

        PreferenciaNotificacao resultado = useCase.executar(USUARIO_ID);

        assertThat(resultado.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(resultado.getPush()).isTrue();
        assertThat(resultado.getEmail()).isFalse();
        verify(preferenciaNotificacaoService, never()).salvar(any());
    }

    @Test
    void semRegistroSmsEWhatsappFicamSemPadraoEnquantoPendenciasNaoForemResolvidas() {
        when(preferenciaNotificacaoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());

        PreferenciaNotificacao resultado = useCase.executar(USUARIO_ID);

        assertThat(resultado.getSms()).isNull();
        assertThat(resultado.getWhatsapp()).isNull();
    }
}
