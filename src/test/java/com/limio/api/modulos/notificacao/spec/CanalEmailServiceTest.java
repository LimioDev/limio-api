package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.actions.service.CanalEmailService;
import com.limio.api.modulos.notificacao.actions.service.EmailAvisoService;
import com.limio.api.modulos.notificacao.enums.TipoNotificacao;

@ExtendWith(MockitoExtension.class)
class CanalEmailServiceTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-16T15:00:00Z");

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private EmailAvisoService emailAvisoService;

    private CanalEmailService canal;
    private Notificacao notificacao;

    @BeforeEach
    void preparar() {
        canal = new CanalEmailService(consultaDadosUsuario, emailAvisoService);
        notificacao = Notificacao.builder()
                .usuarioId(USUARIO_ID)
                .tipo(TipoNotificacao.NOVA_CANDIDATURA)
                .titulo("Novo candidato")
                .corpo("Rafael S. se candidatou a 'Troca'.")
                .enviadaEm(AGORA)
                .build();
    }

    @Test
    void dadoEmailConfirmado_quandoEnvia_entaoMandaProEndereco() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(
                new DadosUsuario("Maria", "maria@example.com", null, AGORA)));
        when(emailAvisoService.enviar("maria@example.com", "Novo candidato", "Rafael S. se candidatou a 'Troca'."))
                .thenReturn(true);

        assertThat(canal.enviar(notificacao)).isTrue();
    }

    @Test
    void dadoEnvioDesligadoNoAmbiente_quandoEnvia_entaoNaoContaComoEnviado() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(
                new DadosUsuario("Maria", "maria@example.com", null, AGORA)));
        when(emailAvisoService.enviar("maria@example.com", "Novo candidato", "Rafael S. se candidatou a 'Troca'."))
                .thenReturn(false);

        assertThat(canal.enviar(notificacao)).isFalse();
    }

    @Test
    void dadoEmailNaoConfirmado_quandoEnvia_entaoNaoTenta() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(
                new DadosUsuario("Maria", "maria@example.com", null, null)));

        assertThat(canal.enviar(notificacao)).isFalse();

        verifyNoInteractions(emailAvisoService);
    }
}
