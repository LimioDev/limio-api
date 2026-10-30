package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.eventos.ServicoIniciadoEvent;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.IniciarServicoUseCase;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

@ExtendWith(MockitoExtension.class)
class IniciarServicoUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ReagendamentoService reagendamentoService;

    @Mock
    private ApplicationEventPublisher eventos;

    private IniciarServicoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new IniciarServicoUseCase(anuncioService, aplicacaoService, reagendamentoService, eventos);
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

    private void comAnuncioEPrestadorEscolhido(Anuncio anuncio) {
        when(anuncioService.buscarPorIdParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.foiEscolhido(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(true);
    }

    @Test
    void dadoPrestadorEscolhido_quandoInicia_entaoAnuncioFicaEmAndamentoEPublicaEvento() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        comAnuncioEPrestadorEscolhido(anuncio);
        when(reagendamentoService.buscarPendentesDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of());
        when(anuncioService.salvar(anuncio)).thenReturn(anuncio);

        Anuncio iniciado = useCase.executar(ANUNCIO_ID, PRESTADOR_ID);

        assertThat(iniciado.getStatus()).isEqualTo(StatusAnuncio.EM_ANDAMENTO);
        assertThat(iniciado.getIniciadoEm()).isNotNull();
        verify(reagendamentoService, never()).salvar(any());

        ArgumentCaptor<ServicoIniciadoEvent> evento = ArgumentCaptor.forClass(ServicoIniciadoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().anuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(evento.getValue().anuncioTitulo()).isEqualTo("Instalar 3 tomadas");
        assertThat(evento.getValue().contratanteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().prestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(evento.getValue().ocorridoEm()).isEqualTo(iniciado.getIniciadoEm());
    }

    @Test
    void dadoPropostaPendente_quandoInicia_entaoEncerraSemEfeito() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        Reagendamento pendente = Reagendamento.builder()
                .anuncioId(ANUNCIO_ID)
                .status(StatusReagendamento.PENDENTE)
                .build();
        comAnuncioEPrestadorEscolhido(anuncio);
        when(reagendamentoService.buscarPendentesDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of(pendente));

        useCase.executar(ANUNCIO_ID, PRESTADOR_ID);

        assertThat(pendente.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
        assertThat(pendente.getRespondidoEm()).isNotNull();
        verify(reagendamentoService).salvar(pendente);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = "PUBLICADO", mode = EnumSource.Mode.EXCLUDE)
    void dadoAnuncioForaDePublicado_quandoInicia_entaoRecusa(StatusAnuncio status) {
        Anuncio anuncio = anuncio(status);
        comAnuncioEPrestadorEscolhido(anuncio);

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID))
                .isInstanceOf(ServicoEstadoInvalidoException.class);

        assertThat(anuncio.getStatus()).isEqualTo(status);
        assertThat(anuncio.getIniciadoEm()).isNull();
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(reagendamentoService, eventos);
    }

    @Test
    void dadoPrestadorNaoEscolhido_quandoInicia_entao404() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        when(anuncioService.buscarPorIdParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.foiEscolhido(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID))
                .isInstanceOf(ServicoNaoEncontradoException.class);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(reagendamentoService, eventos);
    }

    @Test
    void dadoAnuncioInexistente_quandoInicia_entao404() {
        when(anuncioService.buscarPorIdParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID))
                .isInstanceOf(ServicoNaoEncontradoException.class);

        verifyNoInteractions(aplicacaoService, reagendamentoService, eventos);
    }
}
