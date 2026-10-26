package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.modulos.auth.actions.helper.LinkWeb;
import com.limio.api.modulos.auth.actions.helper.TokenRecuperacaoHelper;
import com.limio.api.modulos.auth.actions.usecase.EmitirConfirmacaoEmailUseCase;
import com.limio.api.modulos.auth.eventos.ConfirmacaoEmailSolicitadaEvent;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContato;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoService;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

@ExtendWith(MockitoExtension.class)
class EmitirConfirmacaoEmailUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final String BASE = "https://api.limio.test";
    private static final FinalidadeVerificacao FINALIDADE = FinalidadeVerificacao.CONFIRMACAO_EMAIL;
    private static final Instant AGORA = Instant.parse("2026-10-26T12:00:00Z");

    @Mock
    private VerificacaoContatoService verificacaoContatoService;

    @Mock
    private ApplicationEventPublisher eventos;

    private EmitirConfirmacaoEmailUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new EmitirConfirmacaoEmailUseCase(verificacaoContatoService, eventos, new LinkWeb(BASE),
                Duration.ofHours(24), 3, Duration.ofHours(1));
    }

    private Usuario usuario() {
        Usuario usuario = Usuario.builder().email("maria@example.com").build();
        ReflectionTestUtils.setField(usuario, "id", USUARIO_ID);
        return usuario;
    }

    @Test
    void linkDoEmailApontaParaAPaginaWebComOTokenNoFragmento() {
        when(verificacaoContatoService.pendentesDoUsuario(USUARIO_ID, FINALIDADE)).thenReturn(List.of());

        useCase.emitir(usuario(), AGORA);

        ArgumentCaptor<ConfirmacaoEmailSolicitadaEvent> evento =
                ArgumentCaptor.forClass(ConfirmacaoEmailSolicitadaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().destinatario()).isEqualTo("maria@example.com");
        assertThat(evento.getValue().link()).startsWith(BASE + "/confirmar-email#token=");

        ArgumentCaptor<VerificacaoContato> salvo = ArgumentCaptor.forClass(VerificacaoContato.class);
        verify(verificacaoContatoService).salvar(salvo.capture());
        String token = evento.getValue().link().substring(evento.getValue().link().indexOf("#token=") + 7);
        assertThat(salvo.getValue().getTokenHash()).isEqualTo(TokenRecuperacaoHelper.hash(token));
        assertThat(salvo.getValue().getExpiraEm()).isEqualTo(AGORA.plus(Duration.ofHours(24)));
        assertThat(salvo.getValue().getDestinoHash()).isEqualTo(TokenRecuperacaoHelper.hashDestino("maria@example.com"));
    }

    @Test
    void deveConsumirOsLinksPendentesAntesDeEmitirONovo() {
        VerificacaoContato antigo = VerificacaoContato.builder().finalidade(FINALIDADE).build();
        when(verificacaoContatoService.pendentesDoUsuario(USUARIO_ID, FINALIDADE)).thenReturn(List.of(antigo));

        useCase.emitir(usuario(), AGORA);

        assertThat(antigo.getConsumidoEm()).isEqualTo(AGORA);
    }

    @Test
    void limiteContaAsEmissoesDaJanela() {
        when(verificacaoContatoService.contarDesde(eq(USUARIO_ID), eq(FINALIDADE), any())).thenReturn(2L, 3L);

        assertThat(useCase.limiteAtingido(USUARIO_ID, AGORA)).isFalse();
        assertThat(useCase.limiteAtingido(USUARIO_ID, AGORA)).isTrue();
        verify(verificacaoContatoService, org.mockito.Mockito.times(2))
                .contarDesde(USUARIO_ID, FINALIDADE, AGORA.minus(Duration.ofHours(1)));
    }
}
