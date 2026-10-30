package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

import com.limio.api.comum.eventos.AnuncioCanceladoEvent;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.CancelarAnuncioUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.AnuncioEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class CancelarAnuncioUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ApplicationEventPublisher eventos;

    private CancelarAnuncioUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new CancelarAnuncioUseCase(anuncioService, aplicacaoService, eventos);
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

    private Aplicacao aplicacao(StatusAplicacao status) {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(ANUNCIO_ID)
                .prestadorId(UUID.randomUUID())
                .status(status)
                .dataAplicacao(Instant.now())
                .build();
        ReflectionTestUtils.setField(aplicacao, "id", UUID.randomUUID());
        return aplicacao;
    }

    @Test
    void dadoAnuncioComCandidatos_quandoCancela_entaoEncerraAnuncioECandidaturasEPublicaEvento() {
        Anuncio anuncio = anuncioPublicado();
        Aplicacao enviada1 = aplicacao(StatusAplicacao.ENVIADA);
        Aplicacao enviada2 = aplicacao(StatusAplicacao.ENVIADA);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);
        when(aplicacaoService.buscarAbertasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of(enviada1, enviada2));

        useCase.executar(ANUNCIO_ID, CONTRATANTE_ID);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(anuncio.getEncerradoEm()).isNotNull();
        assertThat(anuncio.getMotivoEncerramento()).isEqualTo("Cancelado pelo Empregador");
        verify(anuncioService).salvar(anuncio);

        assertThat(enviada1.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(enviada1.getMotivoEncerramento()).isEqualTo("Anúncio cancelado pelo Empregador");
        assertThat(enviada2.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        verify(aplicacaoService).salvar(enviada1);
        verify(aplicacaoService).salvar(enviada2);

        ArgumentCaptor<AnuncioCanceladoEvent> evento = ArgumentCaptor.forClass(AnuncioCanceladoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().anuncioId()).isEqualTo(ANUNCIO_ID);
    }

    @Test
    void dadoAnuncioSemCandidatos_quandoCancela_entaoEncerraSoOAnuncio() {
        Anuncio anuncio = anuncioPublicado();
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);
        when(aplicacaoService.buscarAbertasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of());

        useCase.executar(ANUNCIO_ID, CONTRATANTE_ID);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        verify(anuncioService).salvar(anuncio);
        verify(aplicacaoService, never()).salvar(any());
    }

    @Test
    void dadoAnuncioComCandidaturaEscolhida_quandoCancela_entaoRecusa() {
        Anuncio anuncio = anuncioPublicado();
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID))
                .isInstanceOf(AnuncioEstadoInvalidoException.class);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractionsComEventos();
    }

    @Test
    void dadoAnuncioNaoPublicado_quandoCancela_entaoRecusa() {
        Anuncio concluido = anuncioPublicado();
        concluido.setStatus(StatusAnuncio.CONCLUIDO);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(concluido));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID))
                .isInstanceOf(AnuncioEstadoInvalidoException.class);

        assertThat(concluido.getStatus()).isEqualTo(StatusAnuncio.CONCLUIDO);
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractionsComEventos();
    }

    @Test
    void dadoAnuncioDeOutroContratante_quandoCancela_entao404() {
        Anuncio deOutro = anuncioPublicado();
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(deOutro));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, UUID.randomUUID()))
                .isInstanceOf(AnuncioNaoEncontradoException.class);

        verify(aplicacaoService, never()).existeEscolhida(any());
        verifyNoInteractionsComEventos();
    }

    @Test
    void dadoAnuncioInexistente_quandoCancela_entao404() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID))
                .isInstanceOf(AnuncioNaoEncontradoException.class);
    }

    private void verifyNoInteractionsComEventos() {
        org.mockito.Mockito.verifyNoInteractions(eventos);
    }
}
