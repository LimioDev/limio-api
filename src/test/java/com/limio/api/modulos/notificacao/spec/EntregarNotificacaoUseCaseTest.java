package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.NotificacaoService;
import com.limio.api.modulos.notificacao.actions.service.CanalDeEnvioService;
import com.limio.api.modulos.notificacao.actions.service.CanalWhatsappService;
import com.limio.api.modulos.notificacao.actions.usecase.EntregarNotificacaoUseCase;
import com.limio.api.modulos.notificacao.enums.CanalNotificacao;
import com.limio.api.modulos.notificacao.enums.StatusEnvio;
import com.limio.api.modulos.notificacao.enums.TipoNotificacao;
import com.limio.api.modulos.notificacao.envionotificacao.EnvioNotificacao;
import com.limio.api.modulos.notificacao.envionotificacao.EnvioNotificacaoService;
import com.limio.api.modulos.notificacao.eventos.NotificacaoRegistradaEvent;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacaoService;

@ExtendWith(MockitoExtension.class)
class EntregarNotificacaoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID NOTIFICACAO_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-16T15:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Mock
    private NotificacaoService notificacaoService;

    @Mock
    private PreferenciaNotificacaoService preferenciaNotificacaoService;

    @Mock
    private EnvioNotificacaoService envioNotificacaoService;

    @Mock
    private CanalDeEnvioService push;

    @Mock
    private CanalDeEnvioService email;

    private Notificacao notificacao;

    @BeforeEach
    void canais() {
        lenient().when(push.canal()).thenReturn(CanalNotificacao.PUSH);
        lenient().when(email.canal()).thenReturn(CanalNotificacao.EMAIL);
    }

    /** Fora de ordem de propósito: o envio segue a ordem do enum, não a de injeção. */
    private void entregar() {
        new EntregarNotificacaoUseCase(notificacaoService, preferenciaNotificacaoService, envioNotificacaoService,
                List.of(email, new CanalWhatsappService(), push), RELOGIO)
                .aoRegistrarNotificacao(new NotificacaoRegistradaEvent(NOTIFICACAO_ID));
    }

    private void notificacaoGravada() {
        notificacao = Notificacao.builder()
                .usuarioId(USUARIO_ID)
                .tipo(TipoNotificacao.NOVA_CANDIDATURA)
                .titulo("Novo candidato")
                .corpo("Rafael S. se candidatou a 'Troca'.")
                .enviadaEm(AGORA)
                .build();
        ReflectionTestUtils.setField(notificacao, "id", NOTIFICACAO_ID);
        when(notificacaoService.buscarPorId(NOTIFICACAO_ID)).thenReturn(Optional.of(notificacao));
    }

    private void preferencia(boolean push, boolean email, boolean whatsapp) {
        when(preferenciaNotificacaoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(
                PreferenciaNotificacao.builder().usuarioId(USUARIO_ID).push(push).email(email).whatsapp(whatsapp)
                        .build()));
    }

    @SuppressWarnings("unchecked")
    private List<EnvioNotificacao> enviosSalvos() {
        ArgumentCaptor<List<EnvioNotificacao>> envios = ArgumentCaptor.forClass(List.class);
        verify(envioNotificacaoService).salvarTodos(envios.capture());
        return envios.getValue();
    }

    @Test
    void dadoSemPreferenciaSalva_quandoEntrega_entaoUsaOPadraoPushLigadoEEmailDesligado() {
        notificacaoGravada();
        when(preferenciaNotificacaoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());
        when(push.enviar(notificacao)).thenReturn(true);

        entregar();

        assertThat(enviosSalvos()).extracting(EnvioNotificacao::getCanal, EnvioNotificacao::getStatus)
                .containsExactly(tuple(CanalNotificacao.PUSH, StatusEnvio.ENVIADO));
        verify(email, never()).enviar(any());
    }

    @Test
    void dadoFalhaNoPush_quandoEntrega_entaoRegistraAFalhaEOEmailSaiMesmoAssim() {
        notificacaoGravada();
        preferencia(true, true, false);
        when(push.enviar(notificacao)).thenThrow(new IllegalStateException("timeout"));
        when(email.enviar(notificacao)).thenReturn(true);

        entregar();

        List<EnvioNotificacao> envios = enviosSalvos();
        assertThat(envios).extracting(EnvioNotificacao::getCanal, EnvioNotificacao::getStatus)
                .containsExactly(
                        tuple(CanalNotificacao.PUSH, StatusEnvio.FALHOU),
                        tuple(CanalNotificacao.EMAIL, StatusEnvio.ENVIADO));
        assertThat(envios.get(0).getDetalhe()).isEqualTo("IllegalStateException: timeout");
    }

    /** Envio desligado no ambiente, sem aparelho ou sem e-mail confirmado: nada sai, nada é registrado. */
    @Test
    void dadoCanalQueNaoTentou_quandoEntrega_entaoNaoRegistra() {
        notificacaoGravada();
        preferencia(true, true, false);
        when(push.enviar(notificacao)).thenReturn(false);
        when(email.enviar(notificacao)).thenReturn(false);

        entregar();

        assertThat(enviosSalvos()).isEmpty();
    }

    @Test
    void dadoWhatsappLigado_quandoEntrega_entaoNadaSaiSemConsentimento() {
        notificacaoGravada();
        preferencia(false, false, true);

        entregar();

        assertThat(enviosSalvos()).isEmpty();
        verify(push, never()).enviar(any());
        verify(email, never()).enviar(any());
    }

    @Test
    void dadoCanalSemImplementacao_quandoSobe_entaoFalhaNaHora() {
        assertThatThrownBy(() -> new EntregarNotificacaoUseCase(notificacaoService, preferenciaNotificacaoService,
                envioNotificacaoService, List.of(push, email), RELOGIO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSAPP");
    }
}
