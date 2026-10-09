package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.modulos.notificacao.actions.usecase.AtualizarPreferenciaNotificacaoUseCase;
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
        when(preferenciaNotificacaoService.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    private static PreferenciaNotificacao preferencia(boolean push, boolean email, boolean sms, boolean whatsapp) {
        return PreferenciaNotificacao.builder().push(push).email(email).sms(sms).whatsapp(whatsapp).build();
    }

    @Test
    void semRegistroDeveCriarPreferenciaDoUsuarioLogadoComOsQuatroCanais() {
        when(preferenciaNotificacaoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());

        PreferenciaNotificacao salva = useCase.executar(USUARIO_ID, preferencia(true, false, true, false));

        assertThat(salva.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(salva.getPush()).isTrue();
        assertThat(salva.getEmail()).isFalse();
        assertThat(salva.getSms()).isTrue();
        assertThat(salva.getWhatsapp()).isFalse();
    }

    @Test
    void comRegistroDeveAtualizarOMesmoRegistroEmVezDeCriarOutro() {
        PreferenciaNotificacao atual = preferencia(true, false, false, false);
        atual.setUsuarioId(USUARIO_ID);
        when(preferenciaNotificacaoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(atual));

        useCase.executar(USUARIO_ID, preferencia(false, true, false, false));

        ArgumentCaptor<PreferenciaNotificacao> salva = ArgumentCaptor.forClass(PreferenciaNotificacao.class);
        verify(preferenciaNotificacaoService).salvar(salva.capture());
        assertThat(salva.getValue()).isSameAs(atual);
        assertThat(atual.getPush()).isFalse();
        assertThat(atual.getEmail()).isTrue();
    }

    @Test
    void cadaCanalDeveMudarDeFormaIndependente() {
        PreferenciaNotificacao atual = preferencia(true, true, true, true);
        atual.setUsuarioId(USUARIO_ID);
        when(preferenciaNotificacaoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(atual));

        PreferenciaNotificacao salva = useCase.executar(USUARIO_ID, preferencia(true, true, false, true));

        assertThat(salva.getPush()).isTrue();
        assertThat(salva.getEmail()).isTrue();
        assertThat(salva.getSms()).isFalse();
        assertThat(salva.getWhatsapp()).isTrue();
    }

    @Test
    void desligarWhatsappDeveGravarWhatsappFalse() {
        PreferenciaNotificacao atual = preferencia(true, false, false, true);
        atual.setUsuarioId(USUARIO_ID);
        when(preferenciaNotificacaoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(atual));

        PreferenciaNotificacao salva = useCase.executar(USUARIO_ID, preferencia(true, false, false, false));

        assertThat(salva.getWhatsapp()).isFalse();
    }

    @Test
    void naoDeveTrocarODonoDaPreferenciaPeloQueVierNaEntidadeNova() {
        when(preferenciaNotificacaoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());
        PreferenciaNotificacao nova = preferencia(true, false, false, false);
        nova.setUsuarioId(UUID.randomUUID());

        PreferenciaNotificacao salva = useCase.executar(USUARIO_ID, nova);

        assertThat(salva.getUsuarioId()).isEqualTo(USUARIO_ID);
    }
}
