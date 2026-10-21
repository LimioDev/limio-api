package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.modulos.notificacao.actions.usecase.AtualizarPreferenciaNotificacaoUseCase;
import com.limio.api.modulos.notificacao.excecao.WhatsappConsentimentoIndisponivelException;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacaoService;

@ExtendWith(MockitoExtension.class)
class AtualizarPreferenciaNotificacaoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private PreferenciaNotificacaoService preferenciaNotificacaoService;

    private AtualizarPreferenciaNotificacaoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new AtualizarPreferenciaNotificacaoUseCase(preferenciaNotificacaoService);
    }

    private static PreferenciaNotificacao preferencia(boolean push, boolean email, boolean sms, boolean whatsapp) {
        return PreferenciaNotificacao.builder().push(push).email(email).sms(sms).whatsapp(whatsapp).build();
    }

    @Test
    void deveGravarOsQuatroCanaisDoUsuarioLogadoPorUpsertEDevolverOEstadoPersistido() {
        PreferenciaNotificacao persistida = preferencia(true, false, true, false);
        persistida.setUsuarioId(USUARIO_ID);
        when(preferenciaNotificacaoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(persistida));

        PreferenciaNotificacao resultado = useCase.executar(USUARIO_ID, true, false, true, false);

        InOrder ordem = inOrder(preferenciaNotificacaoService);
        ordem.verify(preferenciaNotificacaoService).gravarCanais(USUARIO_ID, true, false, true, false);
        ordem.verify(preferenciaNotificacaoService).buscarPorUsuario(USUARIO_ID);
        assertThat(resultado).isSameAs(persistida);
    }

    @Test
    void whatsappTrueDeveSerRejeitadoSemGravarNada() {
        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, true, false, false, true))
                .isInstanceOf(WhatsappConsentimentoIndisponivelException.class);

        verifyNoInteractions(preferenciaNotificacaoService);
    }

    @Test
    void cadaCanalDeveSerRepassadoDeFormaIndependente() {
        PreferenciaNotificacao persistida = preferencia(true, true, false, false);
        when(preferenciaNotificacaoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(persistida));

        useCase.executar(USUARIO_ID, true, true, false, false);

        org.mockito.Mockito.verify(preferenciaNotificacaoService).gravarCanais(USUARIO_ID, true, true, false, false);
    }
}
