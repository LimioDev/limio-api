package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.eventos.PrestadorEscolhidoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaPlanoPrestador;
import com.limio.api.comum.portas.ConsumoCotaPrestador;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.enums.Plano;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.EscolherPrestadorUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.AnuncioEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.CandidaturaEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.CandidaturaNaoEncontradaException;
import com.limio.api.modulos.anuncio.excecao.CotaEsgotadaCandidatoException;
import com.limio.api.modulos.anuncio.excecao.EscolhaConflitoException;

@ExtendWith(MockitoExtension.class)
class EscolherPrestadorUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ConsultaPlanoPrestador consultaPlanoPrestador;

    @Mock
    private ConsumoCotaPrestador consumoCotaPrestador;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ApplicationEventPublisher eventos;

    @Mock
    private TravaTransacional trava;

    private EscolherPrestadorUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new EscolherPrestadorUseCase(anuncioService, aplicacaoService, consultaPlanoPrestador,
                consumoCotaPrestador, consultaDadosUsuario, eventos, trava);
    }

    private Anuncio anuncio(StatusAnuncio status) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .descricao("Instalar 3 tomadas novas na sala")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(5)))
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    private Aplicacao enviada(UUID prestadorId) {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(ANUNCIO_ID)
                .prestadorId(prestadorId)
                .status(StatusAplicacao.ENVIADA)
                .dataAplicacao(Instant.now())
                .build();
        ReflectionTestUtils.setField(aplicacao, "id", UUID.randomUUID());
        return aplicacao;
    }

    /** Anúncio PUBLICADO, sem escolhida, com as candidaturas dadas ENVIADA e o plano do escolhido. */
    private void cenario(Plano plano, Aplicacao... enviadas) {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.PUBLICADO)));
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);
        when(aplicacaoService.buscarEnviadasDoAnuncioParaAtualizar(ANUNCIO_ID)).thenReturn(List.of(enviadas));
        lenient().when(consultaPlanoPrestador.planoAtual(PRESTADOR_ID)).thenReturn(plano);
    }

    @Test
    void dadoPrestadorFreeComCota_quandoEscolhe_entaoEscolheEncerraAsDemaisConsomeCotaEPublicaEvento() {
        Aplicacao escolhida = enviada(PRESTADOR_ID);
        Aplicacao outra1 = enviada(UUID.randomUUID());
        Aplicacao outra2 = enviada(UUID.randomUUID());
        cenario(Plano.FREE, outra1, escolhida, outra2);
        when(consumoCotaPrestador.consumirEscolha(any(), any())).thenReturn(true);

        Anuncio resultado = useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, escolhida.getId());

        assertThat(resultado.getId()).isEqualTo(ANUNCIO_ID);
        assertThat(resultado.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(escolhida.getStatus()).isEqualTo(StatusAplicacao.ESCOLHIDA);
        assertThat(escolhida.getEscolhidaEm()).isNotNull();
        assertThat(escolhida.getEncerradaEm()).isNull();
        for (Aplicacao outra : List.of(outra1, outra2)) {
            assertThat(outra.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
            assertThat(outra.getEncerradaEm()).isEqualTo(escolhida.getEscolhidaEm());
            assertThat(outra.getMotivoEncerramento()).isEqualTo("Outro prestador escolhido");
            assertThat(outra.getEscolhidaEm()).isNull();
            verify(aplicacaoService).salvar(outra);
        }
        verify(aplicacaoService).salvarEscolha(escolhida);
        verify(consumoCotaPrestador).consumirEscolha(PRESTADOR_ID, escolhida.getEscolhidaEm());

        ArgumentCaptor<PrestadorEscolhidoEvent> evento = ArgumentCaptor.forClass(PrestadorEscolhidoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().anuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(evento.getValue().anuncioTitulo()).isEqualTo("Instalar 3 tomadas");
        assertThat(evento.getValue().candidaturaEscolhidaId()).isEqualTo(escolhida.getId());
        assertThat(evento.getValue().prestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(evento.getValue().contratanteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().candidaturasNaoEscolhidasIds())
                .containsExactlyInAnyOrder(outra1.getId(), outra2.getId());
        assertThat(evento.getValue().ocorridoEm()).isEqualTo(escolhida.getEscolhidaEm());
    }

    @Test
    void dadoPrestadorGoldAtivo_quandoEscolhe_entaoEscolheSemConsumirCota() {
        Aplicacao escolhida = enviada(PRESTADOR_ID);
        cenario(Plano.GOLD, escolhida);

        useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, escolhida.getId());

        assertThat(escolhida.getStatus()).isEqualTo(StatusAplicacao.ESCOLHIDA);
        verifyNoInteractions(consumoCotaPrestador);
        verify(eventos).publishEvent(any(PrestadorEscolhidoEvent.class));
    }

    @Test
    void dadoPrestadorFreeSemCota_quandoEscolhe_entaoRecusaComONomeSemAlterarNada() {
        Aplicacao escolhida = enviada(PRESTADOR_ID);
        Aplicacao outra = enviada(UUID.randomUUID());
        cenario(Plano.FREE, escolhida, outra);
        when(consumoCotaPrestador.consumirEscolha(any(), any())).thenReturn(false);
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(
                new DadosUsuario("Rafael da Silva Souza", "r@example.com", "+5511999998888", Instant.now())));

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, escolhida.getId()))
                .isInstanceOf(CotaEsgotadaCandidatoException.class)
                .hasMessageContaining("Rafael S.")
                .extracting(erro -> ((CotaEsgotadaCandidatoException) erro).getStatus().value()).isEqualTo(409);

        assertThat(escolhida.getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        assertThat(escolhida.getEscolhidaEm()).isNull();
        assertThat(outra.getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        verify(aplicacaoService, never()).salvar(any());
        verify(aplicacaoService, never()).salvarEscolha(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoCotaEsgotadaECandidatoSemDados_quandoEscolhe_entaoMensagemGenerica() {
        Aplicacao escolhida = enviada(PRESTADOR_ID);
        cenario(Plano.FREE, escolhida);
        when(consumoCotaPrestador.consumirEscolha(any(), any())).thenReturn(false);
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, escolhida.getId()))
                .isInstanceOf(CotaEsgotadaCandidatoException.class)
                .hasMessageStartingWith("O candidato atingiu");
    }

    @Test
    void dadaEscolha_quandoExecuta_entaoTravaAnuncioDepoisLinhasDepoisCotaEDepoisGrava() {
        Aplicacao escolhida = enviada(PRESTADOR_ID);
        Aplicacao outra = enviada(UUID.randomUUID());
        cenario(Plano.FREE, escolhida, outra);
        when(consumoCotaPrestador.consumirEscolha(any(), any())).thenReturn(true);

        useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, escolhida.getId());

        InOrder ordem = inOrder(trava, anuncioService, aplicacaoService, consumoCotaPrestador, eventos);
        ordem.verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());
        ordem.verify(anuncioService).buscarPorId(ANUNCIO_ID);
        ordem.verify(aplicacaoService).existeEscolhida(ANUNCIO_ID);
        ordem.verify(aplicacaoService).buscarEnviadasDoAnuncioParaAtualizar(ANUNCIO_ID);
        ordem.verify(consumoCotaPrestador).consumirEscolha(any(), any());
        ordem.verify(aplicacaoService).salvarEscolha(escolhida);
        ordem.verify(aplicacaoService).salvar(outra);
        ordem.verify(eventos).publishEvent(any(PrestadorEscolhidoEvent.class));
    }

    @Test
    void dadoAnuncioQueJaTemEscolhida_quandoEscolheOutra_entaoConflitoSemAlterarNada() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.PUBLICADO)));
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, UUID.randomUUID()))
                .isInstanceOf(EscolhaConflitoException.class);

        verify(aplicacaoService, never()).buscarEnviadasDoAnuncioParaAtualizar(any());
        verifyNoInteractions(consumoCotaPrestador, eventos);
    }

    @Test
    void dadaCandidaturaDeOutroAnuncio_quandoEscolhe_entao404SemAlterarNada() {
        Aplicacao daqui = enviada(UUID.randomUUID());
        cenario(Plano.FREE, daqui);
        Aplicacao deOutroAnuncio = enviada(PRESTADOR_ID);
        ReflectionTestUtils.setField(deOutroAnuncio, "anuncioId", UUID.randomUUID());
        when(aplicacaoService.buscarPorId(deOutroAnuncio.getId())).thenReturn(Optional.of(deOutroAnuncio));

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, deOutroAnuncio.getId()))
                .isInstanceOf(CandidaturaNaoEncontradaException.class);

        assertThat(daqui.getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        assertThat(deOutroAnuncio.getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        verifyNoInteractions(consumoCotaPrestador, eventos);
        verify(aplicacaoService, never()).salvarEscolha(any());
    }

    @Test
    void dadaCandidaturaInexistente_quandoEscolhe_entao404() {
        cenario(Plano.FREE, enviada(UUID.randomUUID()));
        UUID inexistente = UUID.randomUUID();
        when(aplicacaoService.buscarPorId(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, inexistente))
                .isInstanceOf(CandidaturaNaoEncontradaException.class);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAplicacao.class, names = { "RECUSADA", "ENCERRADA", "ESCOLHIDA" })
    void dadaCandidaturaDoAnuncioForaDeEnviada_quandoEscolhe_entao409SemAlterarNada(StatusAplicacao status) {
        Aplicacao outra = enviada(UUID.randomUUID());
        cenario(Plano.FREE, outra);
        Aplicacao jaFinalizada = enviada(PRESTADOR_ID);
        jaFinalizada.setStatus(status);
        when(aplicacaoService.buscarPorId(jaFinalizada.getId())).thenReturn(Optional.of(jaFinalizada));

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, jaFinalizada.getId()))
                .isInstanceOf(CandidaturaEstadoInvalidoException.class);

        assertThat(jaFinalizada.getStatus()).isEqualTo(status);
        assertThat(outra.getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        verifyNoInteractions(consumoCotaPrestador, eventos);
    }

    @Test
    void dadoDonoErradoOuAnuncioInexistente_quandoEscolhe_entao404ComAMesmaResposta() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.PUBLICADO)));
        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), ANUNCIO_ID, UUID.randomUUID()))
                .isInstanceOf(AnuncioNaoEncontradoException.class);

        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, UUID.randomUUID()))
                .isInstanceOf(AnuncioNaoEncontradoException.class);

        verifyNoInteractions(aplicacaoService, consumoCotaPrestador, eventos);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "EM_ANDAMENTO", "AGUARDANDO_VALIDACAO", "CONCLUIDO",
            "ENCERRADO" })
    void dadoAnuncioForaDePublicado_quandoEscolhe_entao409(StatusAnuncio status) {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(status)));

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, UUID.randomUUID()))
                .isInstanceOf(AnuncioEstadoInvalidoException.class);

        verifyNoInteractions(aplicacaoService, consumoCotaPrestador, eventos);
    }

    @Test
    void dadaFalhaAoGravarAEscolha_quandoEscolhe_entaoPropagaSemPublicarEvento() {
        Aplicacao escolhida = enviada(PRESTADOR_ID);
        cenario(Plano.FREE, escolhida);
        when(consumoCotaPrestador.consumirEscolha(any(), any())).thenReturn(true);
        doThrow(new IllegalStateException("falha")).when(aplicacaoService).salvarEscolha(escolhida);

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, escolhida.getId()))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(eventos);
    }
}
