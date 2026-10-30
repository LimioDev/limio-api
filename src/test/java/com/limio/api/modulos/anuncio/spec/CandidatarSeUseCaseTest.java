package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.eventos.CandidaturaCriadaEvent;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.CandidatarSeUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.AnuncioEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.AnuncioProprioException;
import com.limio.api.modulos.anuncio.excecao.CandidaturaDuplicadaException;
import com.limio.api.modulos.anuncio.excecao.ContatosNaoConfirmadosException;
import com.limio.api.modulos.anuncio.excecao.CotaEsgotadaException;
import com.limio.api.modulos.anuncio.excecao.SomentePrestadorCandidataException;

@ExtendWith(MockitoExtension.class)
class CandidatarSeUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final int MAX_ESCOLHAS_GRATUITAS = 4;
    private static final DadosUsuario CONTATOS_CONFIRMADOS =
            new DadosUsuario("Rafael Souza", "rafael@example.com", "+5511999998888", Instant.now(), Instant.now());

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ApplicationEventPublisher eventos;

    private CandidatarSeUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new CandidatarSeUseCase(anuncioService, aplicacaoService, consultaDadosUsuario, eventos,
                MAX_ESCOLHAS_GRATUITAS);
    }

    private Anuncio anuncioPublicado() {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .descricao("Instalar 3 tomadas novas na sala")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(5)))
                .status(StatusAnuncio.PUBLICADO)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    /** Monta os mocks do caminho feliz; cada teste sobrescreve só o que precisa mudar. */
    private void prepararCaminhoFeliz() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(CONTATOS_CONFIRMADOS));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncioPublicado()));
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);
        when(aplicacaoService.existeAbertaDoPar(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(false);
        when(aplicacaoService.contarEscolhidasNoPeriodo(any(), any(), any())).thenReturn(0L);
        when(aplicacaoService.salvarCandidatura(any())).thenAnswer(chamada -> {
            Aplicacao aplicacao = chamada.getArgument(0);
            ReflectionTestUtils.setField(aplicacao, "id", UUID.randomUUID());
            return aplicacao;
        });
    }

    @Test
    void dadoPrestadorElegivel_quandoCandidatarSe_entaoCriaAplicacaoEnviadaEPublicaEvento() {
        prepararCaminhoFeliz();

        Aplicacao resultado = useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.PRESTADOR);

        assertThat(resultado.getAnuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(resultado.getPrestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(resultado.getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        assertThat(resultado.getDataAplicacao()).isNotNull();

        ArgumentCaptor<CandidaturaCriadaEvent> evento = ArgumentCaptor.forClass(CandidaturaCriadaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().candidaturaId()).isEqualTo(resultado.getId());
        assertThat(evento.getValue().anuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(evento.getValue().anuncioTitulo()).isEqualTo("Instalar 3 tomadas");
        assertThat(evento.getValue().contratanteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().prestadorId()).isEqualTo(PRESTADOR_ID);
    }

    @Test
    void dadoPapelAtivoEmpregador_quandoCandidatarSe_entaoRecusaSemConsultarNada() {
        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.EMPREGADOR))
                .isInstanceOf(SomentePrestadorCandidataException.class);

        verifyNoInteractionsComAsDependencias();
    }

    @Test
    void dadoTelefoneNaoConfirmado_quandoCandidatarSe_entaoRecusa() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(
                new DadosUsuario("Rafael Souza", "rafael@example.com", "+5511999998888", Instant.now(), null)));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.PRESTADOR))
                .isInstanceOf(ContatosNaoConfirmadosException.class);

        verify(anuncioService, never()).buscarPorId(any());
    }

    @Test
    void dadoEmailNaoConfirmado_quandoCandidatarSe_entaoRecusa() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(
                new DadosUsuario("Rafael Souza", "rafael@example.com", "+5511999998888", null, Instant.now())));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.PRESTADOR))
                .isInstanceOf(ContatosNaoConfirmadosException.class);
    }

    @Test
    void dadoContaInexistenteOuEncerrada_quandoCandidatarSe_entaoRecusaComoContatosNaoConfirmados() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.PRESTADOR))
                .isInstanceOf(ContatosNaoConfirmadosException.class);
    }

    @Test
    void dadoAnuncioInexistente_quandoCandidatarSe_entaoAnuncioNaoEncontrado() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(CONTATOS_CONFIRMADOS));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.PRESTADOR))
                .isInstanceOf(AnuncioNaoEncontradoException.class);
    }

    @Test
    void dadoAnuncioDoProprioPrestador_quandoCandidatarSe_entaoRecusa() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(CONTATOS_CONFIRMADOS));
        Anuncio proprio = Anuncio.builder()
                .contratanteId(PRESTADOR_ID)
                .titulo("Meu próprio anúncio")
                .descricao("...")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(1)))
                .status(StatusAnuncio.PUBLICADO)
                .build();
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(proprio));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.PRESTADOR))
                .isInstanceOf(AnuncioProprioException.class);

        verify(aplicacaoService, never()).salvarCandidatura(any());
    }

    @Test
    void dadoAnuncioNaoPublicado_quandoCandidatarSe_entaoEstadoInvalido() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(CONTATOS_CONFIRMADOS));
        Anuncio concluido = anuncioPublicado();
        concluido.setStatus(StatusAnuncio.CONCLUIDO);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(concluido));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.PRESTADOR))
                .isInstanceOf(AnuncioEstadoInvalidoException.class);
    }

    @Test
    void dadoAnuncioPublicadoComCandidaturaEscolhida_quandoCandidatarSe_entaoEstadoInvalido() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(CONTATOS_CONFIRMADOS));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncioPublicado()));
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.PRESTADOR))
                .isInstanceOf(AnuncioEstadoInvalidoException.class);

        verify(aplicacaoService, never()).existeAbertaDoPar(any(), any());
    }

    @Test
    void dadoCandidaturaAtivaDoMesmoPar_quandoCandidatarSe_entaoDuplicada() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(CONTATOS_CONFIRMADOS));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncioPublicado()));
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);
        when(aplicacaoService.existeAbertaDoPar(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.PRESTADOR))
                .isInstanceOf(CandidaturaDuplicadaException.class);

        verify(aplicacaoService, never()).salvarCandidatura(any());
    }

    /** RECUSADA/ENCERRADA não contam como "aberta" — pode candidatar de novo (mesma regra do card). */
    @Test
    void dadoCandidaturaAnteriorRecusada_quandoCandidatarDeNovo_entaoPermite() {
        prepararCaminhoFeliz();
        when(aplicacaoService.existeAbertaDoPar(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(false);

        Aplicacao resultado = useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.PRESTADOR);

        assertThat(resultado.getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
    }

    @Test
    void dadoPrestadorFreeComQuatroEscolhasNoMes_quandoCandidatarSe_entaoCotaEsgotada() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(CONTATOS_CONFIRMADOS));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncioPublicado()));
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);
        when(aplicacaoService.existeAbertaDoPar(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(false);
        when(aplicacaoService.contarEscolhidasNoPeriodo(any(), any(), any())).thenReturn(4L);

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.PRESTADOR))
                .isInstanceOf(CotaEsgotadaException.class);

        verify(aplicacaoService, never()).salvarCandidatura(any());
        verifyNoInteractionsComEventos();
    }

    @Test
    void dadoCotaComTresEscolhas_quandoCandidatarSe_entaoPermite() {
        prepararCaminhoFeliz();
        when(aplicacaoService.contarEscolhidasNoPeriodo(any(), any(), any())).thenReturn(3L);

        Aplicacao resultado = useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.PRESTADOR);

        assertThat(resultado.getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
    }

    @Test
    void dadoCandidatura_quandoCandidatarSe_entaoContaAsEscolhasDoMesCivilDeSaoPaulo() {
        prepararCaminhoFeliz();
        when(aplicacaoService.contarEscolhidasNoPeriodo(any(), any(), any())).thenReturn(0L);

        useCase.executar(ANUNCIO_ID, PRESTADOR_ID, PapelUsuario.PRESTADOR);

        ArgumentCaptor<Instant> inicio = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> fim = ArgumentCaptor.forClass(Instant.class);
        verify(aplicacaoService).contarEscolhidasNoPeriodo(any(), inicio.capture(), fim.capture());
        ZoneId saoPaulo = ZoneId.of("America/Sao_Paulo");
        ZonedDateTime inicioDoMes = inicio.getValue().atZone(saoPaulo);
        assertThat(inicioDoMes.getDayOfMonth()).isEqualTo(1);
        assertThat(inicioDoMes.toLocalTime().toSecondOfDay()).isZero();
        assertThat(fim.getValue()).isEqualTo(inicioDoMes.plusMonths(1).toInstant());
    }

    private void verifyNoInteractionsComAsDependencias() {
        org.mockito.Mockito.verifyNoInteractions(anuncioService, aplicacaoService, consultaDadosUsuario);
    }

    private void verifyNoInteractionsComEventos() {
        org.mockito.Mockito.verifyNoInteractions(eventos);
    }
}
