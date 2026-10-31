package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
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

import com.limio.api.comum.eventos.CandidaturaRecusadaEvent;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.RecusarCandidatoUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.AnuncioEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.CandidaturaEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.CandidaturaNaoEncontradaException;

@ExtendWith(MockitoExtension.class)
class RecusarCandidatoUseCaseTest {

    private static final UUID DONO_ID = UUID.randomUUID();
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CANDIDATURA_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ApplicationEventPublisher eventos;

    private RecusarCandidatoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new RecusarCandidatoUseCase(anuncioService, aplicacaoService, eventos);
    }

    private void anuncio(StatusAnuncio status) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(DONO_ID)
                .categoriaId(UUID.randomUUID())
                .titulo("Faxina")
                .descricao("Faxina")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(2)))
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        when(anuncioService.buscarParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
    }

    private Aplicacao candidatura(UUID anuncioId, StatusAplicacao status) {
        Aplicacao aplicacao = Aplicacao.builder().anuncioId(anuncioId).prestadorId(PRESTADOR_ID).status(status)
                .dataAplicacao(Instant.now()).build();
        ReflectionTestUtils.setField(aplicacao, "id", CANDIDATURA_ID);
        when(aplicacaoService.buscarPorId(CANDIDATURA_ID)).thenReturn(Optional.of(aplicacao));
        return aplicacao;
    }

    @Test
    void dadaCandidaturaEnviada_quandoRecusa_entaoViraRecusadaComDataEPublicaEvento() {
        anuncio(StatusAnuncio.PUBLICADO);
        Aplicacao aplicacao = candidatura(ANUNCIO_ID, StatusAplicacao.ENVIADA);

        useCase.executar(DONO_ID, ANUNCIO_ID, CANDIDATURA_ID);

        assertThat(aplicacao.getStatus()).isEqualTo(StatusAplicacao.RECUSADA);
        assertThat(aplicacao.getRecusadaEm()).isNotNull();
        verify(aplicacaoService).salvar(aplicacao);
        ArgumentCaptor<CandidaturaRecusadaEvent> capturado = ArgumentCaptor.forClass(CandidaturaRecusadaEvent.class);
        verify(eventos).publishEvent(capturado.capture());
        CandidaturaRecusadaEvent evento = capturado.getValue();
        assertThat(evento.candidaturaId()).isEqualTo(CANDIDATURA_ID);
        assertThat(evento.anuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(evento.anuncioTitulo()).isEqualTo("Faxina");
        assertThat(evento.contratanteId()).isEqualTo(DONO_ID);
        assertThat(evento.prestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(evento.ocorridoEm()).isEqualTo(aplicacao.getRecusadaEm());
    }

    @Test
    void dadoAnuncioDeOutraPessoa_quandoRecusa_entao404SemTocarNaCandidatura() {
        anuncio(StatusAnuncio.PUBLICADO);

        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), ANUNCIO_ID, CANDIDATURA_ID))
                .isInstanceOf(AnuncioNaoEncontradoException.class);
        verify(aplicacaoService, never()).salvar(any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void dadoAnuncioInexistente_quandoRecusa_entao404() {
        when(anuncioService.buscarParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID, CANDIDATURA_ID))
                .isInstanceOf(AnuncioNaoEncontradoException.class);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = "PUBLICADO", mode = EnumSource.Mode.EXCLUDE)
    void dadoAnuncioForaDePublicado_quandoRecusa_entao409(StatusAnuncio status) {
        anuncio(status);

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID, CANDIDATURA_ID))
                .isInstanceOf(AnuncioEstadoInvalidoException.class);
        verify(aplicacaoService, never()).salvar(any());
    }

    @Test
    void dadaCandidaturaInexistente_quandoRecusa_entao404() {
        anuncio(StatusAnuncio.PUBLICADO);
        when(aplicacaoService.buscarPorId(CANDIDATURA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID, CANDIDATURA_ID))
                .isInstanceOf(CandidaturaNaoEncontradaException.class);
    }

    @Test
    void dadaCandidaturaDeOutroAnuncio_quandoRecusa_entao404() {
        anuncio(StatusAnuncio.PUBLICADO);
        candidatura(UUID.randomUUID(), StatusAplicacao.ENVIADA);

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID, CANDIDATURA_ID))
                .isInstanceOf(CandidaturaNaoEncontradaException.class);
        verify(aplicacaoService, never()).salvar(any());
    }

    @ParameterizedTest
    @EnumSource(value = StatusAplicacao.class, names = "ENVIADA", mode = EnumSource.Mode.EXCLUDE)
    void dadaCandidaturaForaDeEnviada_quandoRecusa_entao409SemMudarNada(StatusAplicacao status) {
        anuncio(StatusAnuncio.PUBLICADO);
        Aplicacao aplicacao = candidatura(ANUNCIO_ID, status);

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID, CANDIDATURA_ID))
                .isInstanceOf(CandidaturaEstadoInvalidoException.class);

        assertThat(aplicacao.getStatus()).isEqualTo(status);
        assertThat(aplicacao.getRecusadaEm()).isNull();
        verify(aplicacaoService, never()).salvar(any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }
}
