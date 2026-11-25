package com.limio.api.modulos.moderacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.unit.DataSize;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.ImagensDeTeste;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.enums.TipoMidia;
import com.limio.api.comum.midia.excecao.FotoInvalidaException;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.portas.ConsultaCandidatura;
import com.limio.api.comum.portas.ConsultaChat;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaServico;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.PartesAnuncio;
import com.limio.api.comum.portas.ResumoCandidatura;
import com.limio.api.comum.portas.ResumoMensagem;
import com.limio.api.modulos.moderacao.actions.usecase.RegistrarDenunciaUseCase;
import com.limio.api.modulos.moderacao.denuncia.AlvoDenuncia;
import com.limio.api.modulos.moderacao.denuncia.Denuncia;
import com.limio.api.modulos.moderacao.denuncia.DenunciaService;
import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;
import com.limio.api.modulos.moderacao.enums.StatusResolucao;
import com.limio.api.modulos.moderacao.eventos.EvidenciaDenunciaEnviadaEvent;
import com.limio.api.modulos.moderacao.excecao.AlvoNaoAcessivelException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RegistrarDenunciaUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-19T15:00:00Z");
    private static final UUID MARIA = UUID.randomUUID();
    private static final UUID CARLOS = UUID.randomUUID();
    private static final UUID JOANA = UUID.randomUUID();
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CANDIDATURA_ID = UUID.randomUUID();
    private static final UUID MENSAGEM_ID = UUID.randomUUID();
    private static final String DESCRICAO = "Não pagou o combinado.";

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ConsultaServico consultaServico;

    @Mock
    private ConsultaCandidatura consultaCandidatura;

    @Mock
    private ConsultaChat consultaChat;

    @Mock
    private DenunciaService denunciaService;

    @Mock
    private MidiaService midiaService;

    @Mock
    private ArmazenamentoService armazenamentoService;

    @Mock
    private TravaTransacional travaTransacional;

    @Mock
    private ApplicationEventPublisher eventos;

    private RegistrarDenunciaUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new RegistrarDenunciaUseCase(consultaDadosUsuario, consultaServico, consultaCandidatura, consultaChat,
                denunciaService, midiaService, armazenamentoService, travaTransacional, eventos, Clock.fixed(AGORA, ZoneOffset.UTC), 72,
                DataSize.ofKilobytes(100));
    }

    @Test
    void perfilDeContaAtivaViraDenunciaAbertaComPrazoDeAnalise() {
        when(consultaDadosUsuario.buscarPorId(CARLOS)).thenReturn(Optional.of(dados()));
        devolverOQueSalvar();

        Denuncia denuncia = registrar(MARIA, AlvoDenuncia.perfil(CARLOS), null);

        assertThat(denuncia.getDenuncianteId()).isEqualTo(MARIA);
        assertThat(denuncia.getDenunciadoId()).isEqualTo(CARLOS);
        assertThat(denuncia.getAnuncioId()).isNull();
        assertThat(denuncia.getMotivo()).isEqualTo(MotivoDenuncia.COMPORTAMENTO_INADEQUADO);
        assertThat(denuncia.getDescricao()).isEqualTo(DESCRICAO);
        assertThat(denuncia.getStatusResolucao()).isEqualTo(StatusResolucao.ABERTA);
        assertThat(denuncia.getPrazoAnalise()).isEqualTo(AGORA.plus(Duration.ofHours(72)));
        assertThat(denuncia.getEvidenciaId()).isNull();
        verifyNoInteractions(armazenamentoService, midiaService, eventos);
    }

    @Test
    void perfilInexistenteOuEncerradoNaoEAcessivel() {
        when(consultaDadosUsuario.buscarPorId(CARLOS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrar(MARIA, AlvoDenuncia.perfil(CARLOS), null))
                .isInstanceOf(AlvoNaoAcessivelException.class);
        verify(denunciaService, never()).salvarAgora(any());
    }

    @Test
    void ninguemDenunciaASiMesmo() {
        when(consultaDadosUsuario.buscarPorId(MARIA)).thenReturn(Optional.of(dados()));

        assertThatThrownBy(() -> registrar(MARIA, AlvoDenuncia.perfil(MARIA), null))
                .isInstanceOf(AlvoNaoAcessivelException.class);
    }

    @Test
    void servicoDenunciaAOutraParte() {
        servicoDeMariaComCarlos();
        devolverOQueSalvar();

        Denuncia peloEmpregador = registrar(MARIA, contexto(null, ANUNCIO_ID, null, null), null);
        Denuncia peloPrestador = registrar(CARLOS, contexto(null, ANUNCIO_ID, null, null), null);

        assertThat(peloEmpregador.getDenunciadoId()).isEqualTo(CARLOS);
        assertThat(peloEmpregador.getAnuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(peloPrestador.getDenunciadoId()).isEqualTo(MARIA);
    }

    @Test
    void terceiroNaoDenunciaServicoDeQueNaoParticipou() {
        servicoDeMariaComCarlos();

        assertThatThrownBy(() -> registrar(JOANA, contexto(null, ANUNCIO_ID, null, null), null))
                .isInstanceOf(AlvoNaoAcessivelException.class);
    }

    @Test
    void anuncioSemEscolhidoQualquerUmDenunciaODonoMenosEleProprio() {
        anuncio(null, true);
        devolverOQueSalvar();

        assertThat(registrar(JOANA, contexto(null, ANUNCIO_ID, null, null), null).getDenunciadoId()).isEqualTo(MARIA);
        assertThatThrownBy(() -> registrar(MARIA, contexto(null, ANUNCIO_ID, null, null), null))
                .isInstanceOf(AlvoNaoAcessivelException.class);
    }

    @Test
    void anuncioCanceladoDepoisDaEscolhaSegueEntreAsPartes() {
        anuncio(CARLOS, false);
        devolverOQueSalvar();

        assertThat(registrar(MARIA, contexto(null, ANUNCIO_ID, null, null), null).getDenunciadoId()).isEqualTo(CARLOS);
        assertThat(registrar(CARLOS, contexto(null, ANUNCIO_ID, null, null), null).getDenunciadoId()).isEqualTo(MARIA);
        assertThatThrownBy(() -> registrar(JOANA, contexto(null, ANUNCIO_ID, null, null), null))
                .isInstanceOf(AlvoNaoAcessivelException.class);
    }

    @Test
    void anuncioForaDoMuralSemNuncaTerEscolhidoNaoEAcessivel() {
        anuncio(null, false);

        assertThatThrownBy(() -> registrar(JOANA, contexto(null, ANUNCIO_ID, null, null), null))
                .isInstanceOf(AlvoNaoAcessivelException.class);
        verify(denunciaService, never()).salvarAgora(any());
    }

    @Test
    void mesmaDenunciaEmAbertoEDevolvidaSemGravarNemEnviarFoto() {
        when(consultaDadosUsuario.buscarPorId(CARLOS)).thenReturn(Optional.of(dados()));
        Denuncia existente = Denuncia.registrar(MARIA, AlvoDenuncia.perfil(CARLOS),
                MotivoDenuncia.COMPORTAMENTO_INADEQUADO, DESCRICAO, null, AGORA);
        when(denunciaService.buscarEmAbertoIgual(MARIA, AlvoDenuncia.perfil(CARLOS),
                MotivoDenuncia.COMPORTAMENTO_INADEQUADO)).thenReturn(Optional.of(existente));

        Denuncia denuncia = registrar(MARIA, AlvoDenuncia.perfil(CARLOS), ImagensDeTeste.png(20, 20));

        assertThat(denuncia).isSameAs(existente);
        verify(travaTransacional).travar(EscopoTrava.DENUNCIA, MARIA.toString());
        verify(denunciaService, never()).salvarAgora(any());
        verifyNoInteractions(armazenamentoService, midiaService, eventos);
    }

    @Test
    void anuncioInexistenteNaoEAcessivel() {
        when(consultaServico.buscarPartes(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrar(MARIA, contexto(null, ANUNCIO_ID, null, null), null))
                .isInstanceOf(AlvoNaoAcessivelException.class);
    }

    @Test
    void candidaturaDenunciaAOutraParteEGuardaOAnuncio() {
        when(consultaCandidatura.buscarPorId(CANDIDATURA_ID)).thenReturn(Optional.of(new ResumoCandidatura(
                CANDIDATURA_ID, "ENVIADA", ANUNCIO_ID, MARIA, CARLOS, "Instalar 3 tomadas", "Elétrica")));
        devolverOQueSalvar();

        Denuncia denuncia = registrar(CARLOS, contexto(null, null, CANDIDATURA_ID, null), null);

        assertThat(denuncia.getDenunciadoId()).isEqualTo(MARIA);
        assertThat(denuncia.getCandidaturaId()).isEqualTo(CANDIDATURA_ID);
        assertThat(denuncia.getAnuncioId()).isEqualTo(ANUNCIO_ID);
        assertThatThrownBy(() -> registrar(JOANA, contexto(null, null, CANDIDATURA_ID, null), null))
                .isInstanceOf(AlvoNaoAcessivelException.class);
    }

    @Test
    void mensagemDenunciaORemetenteEGuardaOContextoDoChat() {
        mensagemDeCarlos();
        devolverOQueSalvar();

        Denuncia denuncia = registrar(MARIA, contexto(CARLOS, null, null, MENSAGEM_ID), null);

        assertThat(denuncia.getDenunciadoId()).isEqualTo(CARLOS);
        assertThat(denuncia.getMensagemId()).isEqualTo(MENSAGEM_ID);
        assertThat(denuncia.getCandidaturaId()).isEqualTo(CANDIDATURA_ID);
        assertThat(denuncia.getAnuncioId()).isEqualTo(ANUNCIO_ID);
    }

    @Test
    void mensagemPropriaOuDeChatAlheioNaoEAcessivel() {
        mensagemDeCarlos();

        assertThatThrownBy(() -> registrar(CARLOS, contexto(null, null, null, MENSAGEM_ID), null))
                .isInstanceOf(AlvoNaoAcessivelException.class);
        assertThatThrownBy(() -> registrar(JOANA, contexto(null, null, null, MENSAGEM_ID), null))
                .isInstanceOf(AlvoNaoAcessivelException.class);
    }

    @Test
    void denunciadoInformadoPrecisaBaterComOContexto() {
        servicoDeMariaComCarlos();

        assertThatThrownBy(() -> registrar(MARIA, contexto(JOANA, ANUNCIO_ID, null, null), null))
                .isInstanceOf(AlvoNaoAcessivelException.class);
    }

    @Test
    void fotoViraMidiaDeEvidenciaLigadaADenuncia() {
        when(consultaDadosUsuario.buscarPorId(CARLOS)).thenReturn(Optional.of(dados()));
        when(midiaService.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        devolverOQueSalvar();

        registrar(MARIA, AlvoDenuncia.perfil(CARLOS), ImagensDeTeste.png(20, 20));

        verify(armazenamentoService).enviar(startsWith("evidencias-denuncia/" + MARIA + "/"), any(), eq("image/png"));
        ArgumentCaptor<Midia> midia = ArgumentCaptor.forClass(Midia.class);
        verify(midiaService).salvar(midia.capture());
        assertThat(midia.getValue().getTipo()).isEqualTo(TipoMidia.EVIDENCIA_DENUNCIA);
        assertThat(midia.getValue().getUsuarioId()).isEqualTo(MARIA);
        assertThat(midia.getValue().getAnuncioId()).isNull();
        verify(eventos).publishEvent(any(EvidenciaDenunciaEnviadaEvent.class));
    }

    @Test
    void fotoInvalidaERecusadaSemGravarNada() {
        when(consultaDadosUsuario.buscarPorId(CARLOS)).thenReturn(Optional.of(dados()));

        assertThatThrownBy(() -> registrar(MARIA, AlvoDenuncia.perfil(CARLOS), ImagensDeTeste.gif(20, 20)))
                .isInstanceOf(FotoInvalidaException.class);
        assertThatThrownBy(() -> registrar(MARIA, AlvoDenuncia.perfil(CARLOS), new byte[0]))
                .isInstanceOf(FotoInvalidaException.class);
        assertThatThrownBy(() -> registrar(MARIA, AlvoDenuncia.perfil(CARLOS), new byte[200 * 1024]))
                .isInstanceOf(FotoInvalidaException.class);
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
        verify(denunciaService, never()).salvarAgora(any());
    }

    @Test
    void alvoInacessivelERecusadoAntesDeOlharAFoto() {
        servicoDeMariaComCarlos();

        assertThatThrownBy(() -> registrar(JOANA, contexto(null, ANUNCIO_ID, null, null), new byte[0]))
                .isInstanceOf(AlvoNaoAcessivelException.class);
    }

    private Denuncia registrar(UUID denuncianteId, AlvoDenuncia alvo, byte[] foto) {
        return useCase.executar(denuncianteId, alvo, MotivoDenuncia.COMPORTAMENTO_INADEQUADO, DESCRICAO, foto);
    }

    private static AlvoDenuncia contexto(UUID denunciadoId, UUID anuncioId, UUID candidaturaId, UUID mensagemId) {
        return new AlvoDenuncia(denunciadoId, anuncioId, candidaturaId, mensagemId);
    }

    private void servicoDeMariaComCarlos() {
        anuncio(CARLOS, false);
    }

    private void anuncio(UUID escolhidoId, boolean noMural) {
        when(consultaServico.buscarPartes(ANUNCIO_ID))
                .thenReturn(Optional.of(new PartesAnuncio(ANUNCIO_ID, MARIA, escolhidoId, noMural)));
    }

    private void mensagemDeCarlos() {
        when(consultaChat.buscarMensagem(MENSAGEM_ID)).thenReturn(Optional.of(
                new ResumoMensagem(MENSAGEM_ID, CARLOS, CANDIDATURA_ID, ANUNCIO_ID, MARIA, CARLOS)));
    }

    private void devolverOQueSalvar() {
        when(denunciaService.salvarAgora(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private static DadosUsuario dados() {
        return new DadosUsuario("Carlos Souza", "carlos@example.com", "+5511988887777", AGORA);
    }
}
