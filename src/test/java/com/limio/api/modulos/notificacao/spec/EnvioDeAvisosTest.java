package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.eventos.PrestadorEscolhidoEvent;
import com.limio.api.comum.portas.ConsultaCandidatura;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoCandidatura;
import com.limio.api.modulos.notificacao.Aviso;
import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.NotificacaoService;
import com.limio.api.modulos.notificacao.actions.helper.AvisoHelper;
import com.limio.api.modulos.notificacao.actions.service.EmailAvisoService;
import com.limio.api.modulos.notificacao.actions.service.PushExpoService;
import com.limio.api.modulos.notificacao.actions.usecase.EntregarNotificacaoUseCase;
import com.limio.api.modulos.notificacao.actions.usecase.RegistrarAvisosUseCase;
import com.limio.api.modulos.notificacao.aparelhopush.AparelhoPushService;
import com.limio.api.modulos.notificacao.enums.CanalNotificacao;
import com.limio.api.modulos.notificacao.enums.StatusEnvio;
import com.limio.api.modulos.notificacao.enums.TipoNotificacao;
import com.limio.api.modulos.notificacao.envionotificacao.EnvioNotificacao;
import com.limio.api.modulos.notificacao.envionotificacao.EnvioNotificacaoService;
import com.limio.api.modulos.notificacao.eventos.NotificacaoRegistradaEvent;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacaoService;

@ExtendWith(MockitoExtension.class)
class EnvioDeAvisosTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID NOTIFICACAO_ID = UUID.randomUUID();
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-16T15:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);
    private static final String TOKEN = "ExponentPushToken[abc]";

    @Mock
    private NotificacaoService notificacaoService;

    @Mock
    private PreferenciaNotificacaoService preferenciaNotificacaoService;

    @Mock
    private AparelhoPushService aparelhoPushService;

    @Mock
    private EnvioNotificacaoService envioNotificacaoService;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ConsultaCandidatura consultaCandidatura;

    @Mock
    private PushExpoService pushExpoService;

    @Mock
    private EmailAvisoService emailAvisoService;

    @Mock
    private ApplicationEventPublisher eventos;

    // ---------- textos ----------

    @Test
    void novoServicoNaArea_usaVirgulaDecimalEUmaCasa() {
        Aviso aviso = AvisoHelper.novoServicoNaArea(USUARIO_ID, ANUNCIO_ID, "Elétrica", "Perdizes", 3.4321);

        assertThat(aviso.titulo()).isEqualTo("Novo serviço na sua área");
        assertThat(aviso.corpo()).isEqualTo("Elétrica em Perdizes, a 3,4 km de distância.");
        assertThat(aviso.tipo()).isEqualTo(TipoNotificacao.NOVO_SERVICO_NA_AREA);
        assertThat(aviso.anuncioId()).isEqualTo(ANUNCIO_ID);
    }

    @Test
    void checkoutEnviado_segueOTextoDoCard() {
        Aviso aviso = AvisoHelper.checkoutEnviado(USUARIO_ID, ANUNCIO_ID, "Rafael S.", "Troca de chuveiro");

        assertThat(aviso.titulo()).isEqualTo("Check-out enviado");
        assertThat(aviso.corpo())
                .isEqualTo("Rafael S. enviou a foto de 'Troca de chuveiro'. Valide para finalizar o serviço.");
    }

    @Test
    void avisoDeAnuncio_cortaOAssuntoNoTamanhoDoTitulo() {
        Aviso aviso = AvisoHelper.avisoDeAnuncio(USUARIO_ID, ANUNCIO_ID, "x".repeat(200));

        assertThat(aviso.titulo()).hasSize(120);
    }

    // ---------- registrar ----------

    @Test
    void dadoEscolha_quandoRegistra_entaoAvisaOEscolhidoECadaNaoEscolhido() {
        UUID escolhido = UUID.randomUUID();
        UUID naoEscolhido = UUID.randomUUID();
        UUID candidaturaNaoEscolhida = UUID.randomUUID();
        when(consultaCandidatura.buscarPorId(candidaturaNaoEscolhida)).thenReturn(Optional.of(new ResumoCandidatura(
                candidaturaNaoEscolhida, "ENCERRADA", ANUNCIO_ID, UUID.randomUUID(), naoEscolhido, "Troca", "Elétrica")));
        when(notificacaoService.salvar(any())).thenAnswer(chamada -> comId(chamada.getArgument(0)));
        RegistrarAvisosUseCase useCase = new RegistrarAvisosUseCase(notificacaoService, consultaDadosUsuario,
                consultaCandidatura, eventos, RELOGIO);

        useCase.aoEscolherPrestador(new PrestadorEscolhidoEvent(ANUNCIO_ID, "Troca", UUID.randomUUID(), escolhido,
                UUID.randomUUID(), List.of(candidaturaNaoEscolhida), AGORA));

        ArgumentCaptor<Notificacao> salvas = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoService, org.mockito.Mockito.times(2)).salvar(salvas.capture());
        assertThat(salvas.getAllValues()).extracting(Notificacao::getUsuarioId, Notificacao::getTipo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(escolhido, TipoNotificacao.PRESTADOR_ESCOLHIDO),
                        org.assertj.core.groups.Tuple.tuple(naoEscolhido, TipoNotificacao.CANDIDATURA_ENCERRADA));
        verify(eventos, org.mockito.Mockito.times(2)).publishEvent(any(NotificacaoRegistradaEvent.class));
    }

    // ---------- entregar ----------

    private EntregarNotificacaoUseCase entregar() {
        return new EntregarNotificacaoUseCase(notificacaoService, preferenciaNotificacaoService, aparelhoPushService,
                envioNotificacaoService, consultaDadosUsuario, pushExpoService, emailAvisoService, RELOGIO);
    }

    private void notificacaoGravada() {
        Notificacao notificacao = comId(Notificacao.builder()
                .usuarioId(USUARIO_ID)
                .anuncioId(ANUNCIO_ID)
                .tipo(TipoNotificacao.NOVA_CANDIDATURA)
                .titulo("Novo candidato")
                .corpo("Rafael S. se candidatou a 'Troca'.")
                .enviadaEm(AGORA)
                .build());
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
        when(aparelhoPushService.tokensDoUsuario(USUARIO_ID)).thenReturn(List.of(TOKEN));
        when(pushExpoService.enviar(anyList(), anyString(), anyString(), anyMap())).thenReturn(List.of());

        entregar().aoRegistrarNotificacao(new NotificacaoRegistradaEvent(NOTIFICACAO_ID));

        assertThat(enviosSalvos()).extracting(EnvioNotificacao::getCanal).containsExactly(CanalNotificacao.PUSH);
        verifyNoInteractions(emailAvisoService);
    }

    @Test
    void dadoPush_quandoEntrega_entaoMandaOsDadosDoToqueEApagaTokenInvalido() {
        notificacaoGravada();
        preferencia(true, false, false);
        when(aparelhoPushService.tokensDoUsuario(USUARIO_ID)).thenReturn(List.of(TOKEN));
        when(pushExpoService.enviar(anyList(), anyString(), anyString(), anyMap())).thenReturn(List.of(TOKEN));

        entregar().aoRegistrarNotificacao(new NotificacaoRegistradaEvent(NOTIFICACAO_ID));

        verify(pushExpoService).enviar(eq(List.of(TOKEN)), eq("Novo candidato"), anyString(),
                eq(Map.of("notificacaoId", NOTIFICACAO_ID.toString(), "tipo", "NOVA_CANDIDATURA",
                        "anuncioId", ANUNCIO_ID.toString())));
        verify(aparelhoPushService).apagarTokens(List.of(TOKEN));
    }

    @Test
    void dadoPushLigadoSemAparelho_quandoEntrega_entaoNaoTentaNemRegistra() {
        notificacaoGravada();
        preferencia(true, false, false);
        when(aparelhoPushService.tokensDoUsuario(USUARIO_ID)).thenReturn(List.of());

        entregar().aoRegistrarNotificacao(new NotificacaoRegistradaEvent(NOTIFICACAO_ID));

        verifyNoInteractions(pushExpoService);
        assertThat(enviosSalvos()).isEmpty();
    }

    @Test
    void dadoFalhaNoPush_quandoEntrega_entaoRegistraAFalhaEOEmailSaiMesmoAssim() {
        notificacaoGravada();
        preferencia(true, true, false);
        when(aparelhoPushService.tokensDoUsuario(USUARIO_ID)).thenReturn(List.of(TOKEN));
        when(pushExpoService.enviar(anyList(), anyString(), anyString(), anyMap()))
                .thenThrow(new IllegalStateException("timeout"));
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(
                new DadosUsuario("Maria", "maria@example.com", null, AGORA)));

        entregar().aoRegistrarNotificacao(new NotificacaoRegistradaEvent(NOTIFICACAO_ID));

        assertThat(enviosSalvos()).extracting(EnvioNotificacao::getCanal, EnvioNotificacao::getStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(CanalNotificacao.PUSH, StatusEnvio.FALHOU),
                        org.assertj.core.groups.Tuple.tuple(CanalNotificacao.EMAIL, StatusEnvio.ENVIADO));
        verify(emailAvisoService).enviar(eq("maria@example.com"), eq("Novo candidato"), anyString());
    }

    @Test
    void dadoEmailNaoConfirmado_quandoEntrega_entaoNaoMandaEmail() {
        notificacaoGravada();
        preferencia(false, true, false);
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(
                new DadosUsuario("Maria", "maria@example.com", null, null)));

        entregar().aoRegistrarNotificacao(new NotificacaoRegistradaEvent(NOTIFICACAO_ID));

        verify(emailAvisoService, never()).enviar(anyString(), anyString(), anyString());
        assertThat(enviosSalvos()).isEmpty();
    }

    @Test
    void dadoWhatsappLigado_quandoEntrega_entaoNadaSaiSemConsentimento() {
        notificacaoGravada();
        preferencia(false, false, true);

        entregar().aoRegistrarNotificacao(new NotificacaoRegistradaEvent(NOTIFICACAO_ID));

        assertThat(enviosSalvos()).isEmpty();
        verifyNoInteractions(pushExpoService, emailAvisoService);
    }

    private static Notificacao comId(Notificacao notificacao) {
        if (notificacao.getId() == null) {
            ReflectionTestUtils.setField(notificacao, "id", UUID.randomUUID());
        }
        return notificacao;
    }
}
