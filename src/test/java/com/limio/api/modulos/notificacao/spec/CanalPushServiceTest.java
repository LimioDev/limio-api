package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.actions.service.CanalPushService;
import com.limio.api.modulos.notificacao.actions.service.PushExpoService;
import com.limio.api.modulos.notificacao.actions.service.PushExpoService.ResultadoPush;
import com.limio.api.modulos.notificacao.aparelhopush.AparelhoPushService;
import com.limio.api.modulos.notificacao.enums.TipoNotificacao;

@ExtendWith(MockitoExtension.class)
class CanalPushServiceTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID NOTIFICACAO_ID = UUID.randomUUID();
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final String TOKEN = "ExponentPushToken[abc]";

    @Mock
    private AparelhoPushService aparelhoPushService;

    @Mock
    private PushExpoService pushExpoService;

    private CanalPushService canal;
    private Notificacao notificacao;

    @BeforeEach
    void preparar() {
        canal = new CanalPushService(aparelhoPushService, pushExpoService);
        notificacao = Notificacao.builder()
                .usuarioId(USUARIO_ID)
                .anuncioId(ANUNCIO_ID)
                .tipo(TipoNotificacao.NOVA_CANDIDATURA)
                .titulo("Novo candidato")
                .corpo("Rafael S. se candidatou a 'Troca'.")
                .enviadaEm(Instant.parse("2026-11-16T15:00:00Z"))
                .build();
        ReflectionTestUtils.setField(notificacao, "id", NOTIFICACAO_ID);
    }

    @Test
    void dadoAparelho_quandoEnvia_entaoMandaOsDadosDoToqueEApagaTokenInvalido() {
        when(aparelhoPushService.tokensDoUsuario(USUARIO_ID)).thenReturn(List.of(TOKEN));
        when(pushExpoService.enviar(anyList(), anyString(), anyString(), anyMap()))
                .thenReturn(ResultadoPush.enviado(List.of(TOKEN)));

        assertThat(canal.enviar(notificacao)).isTrue();

        verify(pushExpoService).enviar(eq(List.of(TOKEN)), eq("Novo candidato"), anyString(),
                eq(Map.of("notificacaoId", NOTIFICACAO_ID.toString(), "tipo", "NOVA_CANDIDATURA",
                        "anuncioId", ANUNCIO_ID.toString())));
        verify(aparelhoPushService).apagarTokens(List.of(TOKEN));
    }

    @Test
    void dadoSemAparelho_quandoEnvia_entaoNaoTenta() {
        when(aparelhoPushService.tokensDoUsuario(USUARIO_ID)).thenReturn(List.of());

        assertThat(canal.enviar(notificacao)).isFalse();

        verifyNoInteractions(pushExpoService);
    }

    @Test
    void dadoEnvioDesligadoNoAmbiente_quandoEnvia_entaoNaoContaComoEnviado() {
        when(aparelhoPushService.tokensDoUsuario(USUARIO_ID)).thenReturn(List.of(TOKEN));
        when(pushExpoService.enviar(anyList(), anyString(), anyString(), anyMap()))
                .thenReturn(ResultadoPush.naoTentou());

        assertThat(canal.enviar(notificacao)).isFalse();
    }

    @Test
    void dadoUmLoteComFalha_quandoEnvia_entaoApagaOsInvalidosDosOutrosLotesEDepoisFalha() {
        when(aparelhoPushService.tokensDoUsuario(USUARIO_ID)).thenReturn(List.of(TOKEN));
        when(pushExpoService.enviar(anyList(), anyString(), anyString(), anyMap()))
                .thenReturn(new ResultadoPush(true, List.of(TOKEN), Optional.of("lote 2 de 2: timeout")));

        assertThatThrownBy(() -> canal.enviar(notificacao))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("lote 2 de 2: timeout");

        verify(aparelhoPushService).apagarTokens(List.of(TOKEN));
    }
}
