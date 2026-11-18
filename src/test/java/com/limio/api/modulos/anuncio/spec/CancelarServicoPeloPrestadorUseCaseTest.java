package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.eventos.ServicoCanceladoPeloPrestadorEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.CancelarServicoPeloPrestadorUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/** UC43 "Prestador cancelar após ser escolhido" (ETI-96), sem Spring: o que o caso de uso decide e grava. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CancelarServicoPeloPrestadorUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-18T15:00:00Z");

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ReagendamentoService reagendamentoService;

    @Mock
    private ApplicationEventPublisher eventos;

    @Mock
    private TravaTransacional trava;

    private CancelarServicoPeloPrestadorUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new CancelarServicoPeloPrestadorUseCase(anuncioService, aplicacaoService, reagendamentoService,
                eventos, trava, Clock.fixed(AGORA, ZoneOffset.UTC));
        lenient().when(anuncioService.salvar(any(Anuncio.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(reagendamentoService.buscarPendentesDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of());
    }

    // ---------------------------------------------------------------- cenário

    private Anuncio anuncio(StatusAnuncio status) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .dataHoraPretendida(AGORA.plus(Duration.ofHours(2)))
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    private Aplicacao escolhida() {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(ANUNCIO_ID)
                .prestadorId(PRESTADOR_ID)
                .status(StatusAplicacao.ESCOLHIDA)
                .escolhidaEm(AGORA.minus(Duration.ofDays(2)))
                .cotaConsumida(true)
                .build();
        ReflectionTestUtils.setField(aplicacao, "id", UUID.randomUUID());
        return aplicacao;
    }

    private Reagendamento pendente() {
        return Reagendamento.builder()
                .anuncioId(ANUNCIO_ID)
                .dataAnterior(AGORA.plus(Duration.ofHours(2)))
                .dataProposta(AGORA.plus(Duration.ofDays(2)))
                .status(StatusReagendamento.PENDENTE)
                .build();
    }

    private Aplicacao darComoCombinado(StatusAnuncio status) {
        Aplicacao escolhida = escolhida();
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(status)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida));
        when(aplicacaoService.buscarEscolhidaDoAnuncioParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.of(escolhida));
        return escolhida;
    }

    private void verificarNadaGravadoNemPublicado() {
        verify(anuncioService, never()).salvar(any(Anuncio.class));
        verify(aplicacaoService, never()).salvar(any(Aplicacao.class));
        verify(reagendamentoService, never()).salvar(any(Reagendamento.class));
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    // ---------------------------------------------------------------- caminho feliz

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO" })
    void dadoServicoEmAberto_quandoPrestadorEscolhidoCancela_entaoEncerraAnuncioECandidatura(StatusAnuncio status) {
        Aplicacao escolhida = darComoCombinado(status);

        Anuncio resultado = useCase.executar(PRESTADOR_ID, ANUNCIO_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(resultado.getEncerradoEm()).isEqualTo(AGORA);
        assertThat(resultado.getTipoCancelamento()).isNull();
        assertThat(escolhida.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(escolhida.getEncerradaEm()).isEqualTo(AGORA);
        verify(aplicacaoService).salvar(escolhida);
    }

    @Test
    void dadoCancelamento_quandoExecuta_entaoTravaOAnuncioAntesDeLerEDepoisALinhaDaCandidatura() {
        darComoCombinado(StatusAnuncio.PUBLICADO);

        useCase.executar(PRESTADOR_ID, ANUNCIO_ID);

        InOrder ordem = inOrder(trava, anuncioService, aplicacaoService);
        ordem.verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());
        ordem.verify(anuncioService).buscarPorId(ANUNCIO_ID);
        ordem.verify(aplicacaoService).buscarEscolhidaDoAnuncio(ANUNCIO_ID);
        ordem.verify(aplicacaoService).buscarEscolhidaDoAnuncioParaAtualizar(ANUNCIO_ID);
    }

    @Test
    void dadoPropostaDeReagendamentoPendente_quandoCancela_entaoPropostaEncerradaSemEfeito() {
        darComoCombinado(StatusAnuncio.PUBLICADO);
        Reagendamento pendente = pendente();
        when(reagendamentoService.buscarPendentesDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of(pendente));

        useCase.executar(PRESTADOR_ID, ANUNCIO_ID);

        assertThat(pendente.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
        assertThat(pendente.getRespondidoEm()).isEqualTo(AGORA);
        verify(reagendamentoService).salvar(pendente);
    }

    @Test
    void dadoSemPropostaPendente_quandoCancela_entaoNadaDeReagendamentoEGravado() {
        darComoCombinado(StatusAnuncio.PUBLICADO);

        useCase.executar(PRESTADOR_ID, ANUNCIO_ID);

        verify(reagendamentoService, never()).salvar(any(Reagendamento.class));
    }

    @Test
    void dadoCancelamento_quandoConfirma_entaoPublicaOEventoComOsIdsEOTitulo() {
        Aplicacao escolhida = darComoCombinado(StatusAnuncio.PUBLICADO);

        useCase.executar(PRESTADOR_ID, ANUNCIO_ID);

        ArgumentCaptor<ServicoCanceladoPeloPrestadorEvent> captor = ArgumentCaptor
                .forClass(ServicoCanceladoPeloPrestadorEvent.class);
        verify(eventos).publishEvent(captor.capture());
        ServicoCanceladoPeloPrestadorEvent evento = captor.getValue();
        assertThat(evento.anuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(evento.anuncioTitulo()).isEqualTo("Instalar 3 tomadas");
        assertThat(evento.candidaturaEscolhidaId()).isEqualTo(escolhida.getId());
        assertThat(evento.contratanteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.prestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(evento.ocorridoEm()).isEqualTo(AGORA);
    }

    @Test
    void dadoPrestadorFree_quandoCancela_entaoACotaContinuaConsumida() {
        Aplicacao escolhida = darComoCombinado(StatusAnuncio.PUBLICADO);

        useCase.executar(PRESTADOR_ID, ANUNCIO_ID);

        assertThat(escolhida.isCotaConsumida()).isTrue();
        assertThat(escolhida.getCotaEstornadaEm()).isNull();
    }

    // ---------------------------------------------------------------- recusas

    @Test
    void dadoAguardandoValidacao_quandoPrestadorCancela_entaoRecusaESegueAValidacao() {
        when(anuncioService.buscarPorId(ANUNCIO_ID))
                .thenReturn(Optional.of(anuncio(StatusAnuncio.AGUARDANDO_VALIDACAO)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));

        assertThatThrownBy(() -> useCase.executar(PRESTADOR_ID, ANUNCIO_ID))
                .isInstanceOf(ServicoEstadoInvalidoException.class);

        verificarNadaGravadoNemPublicado();
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "CONCLUIDO", "ENCERRADO" })
    void dadoServicoEmEstadoTerminal_quandoPrestadorCancela_entaoRecusa(StatusAnuncio status) {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(status)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));

        assertThatThrownBy(() -> useCase.executar(PRESTADOR_ID, ANUNCIO_ID))
                .isInstanceOf(ServicoEstadoInvalidoException.class);

        verificarNadaGravadoNemPublicado();
    }

    @Test
    void dadoAnuncioEmAbertoSemCandidaturaEscolhidaTravavel_quandoCancela_entaoRecusa() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.PUBLICADO)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));
        when(aplicacaoService.buscarEscolhidaDoAnuncioParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(PRESTADOR_ID, ANUNCIO_ID))
                .isInstanceOf(ServicoEstadoInvalidoException.class);

        verificarNadaGravadoNemPublicado();
    }

    @Test
    void dadoServicoSemNinguemEscolhido_quandoCancela_entao404() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.PUBLICADO)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(PRESTADOR_ID, ANUNCIO_ID))
                .isInstanceOf(ServicoNaoEncontradoException.class);

        verificarNadaGravadoNemPublicado();
    }

    @Test
    void dadoServicoInexistente_quandoCancela_entao404() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(PRESTADOR_ID, ANUNCIO_ID))
                .isInstanceOf(ServicoNaoEncontradoException.class);

        verificarNadaGravadoNemPublicado();
    }

    @Test
    void dadoPrestadorNaoEscolhido_quandoCancela_entao404SemRevelarOEstado() {
        UUID outro = UUID.randomUUID();
        when(anuncioService.buscarPorId(ANUNCIO_ID))
                .thenReturn(Optional.of(anuncio(StatusAnuncio.AGUARDANDO_VALIDACAO)));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));

        assertThatThrownBy(() -> useCase.executar(outro, ANUNCIO_ID))
                .isInstanceOf(ServicoNaoEncontradoException.class);

        verificarNadaGravadoNemPublicado();
    }
}
