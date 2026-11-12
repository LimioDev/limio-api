package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.eventos.AnuncioExpiradoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.ExpirarAnuncioUseCase;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

@ExtendWith(MockitoExtension.class)
class ExpirarAnuncioUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T12:00:00Z");
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ApplicationEventPublisher eventos;

    @Mock
    private TravaTransacional trava;

    private ExpirarAnuncioUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ExpirarAnuncioUseCase(anuncioService, aplicacaoService, eventos, trava);
    }

    private Anuncio anuncio(StatusAnuncio status, Instant dataHoraPretendida) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .dataHoraPretendida(dataHoraPretendida)
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    @Test
    void dadoAnuncioVencidoSemCandidatura_quandoExecuta_entaoExpiraSalvaEPublicaEventoUmaVez() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, AGORA.minusSeconds(60));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.existeAlguma(ANUNCIO_ID)).thenReturn(false);

        boolean expirou = useCase.executar(ANUNCIO_ID, AGORA);

        assertThat(expirou).isTrue();
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(anuncio.getEncerradoEm()).isEqualTo(AGORA);
        assertThat(anuncio.getMotivoEncerramento()).isEqualTo("Expirado sem candidatos");

        InOrder ordem = inOrder(trava, anuncioService, eventos);
        ordem.verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());
        ordem.verify(anuncioService).buscarPorId(ANUNCIO_ID);
        ordem.verify(anuncioService).salvar(anuncio);

        ArgumentCaptor<AnuncioExpiradoEvent> evento = ArgumentCaptor.forClass(AnuncioExpiradoEvent.class);
        ordem.verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isEqualTo(
                new AnuncioExpiradoEvent(ANUNCIO_ID, CONTRATANTE_ID, "Instalar 3 tomadas", AGORA));
    }

    @Test
    void dadoAnuncioComCandidaturaEmQualquerEstado_quandoExecuta_entaoNaoExpiraNemPublica() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, AGORA.minusSeconds(60));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.existeAlguma(ANUNCIO_ID)).thenReturn(true);

        assertThat(useCase.executar(ANUNCIO_ID, AGORA)).isFalse();

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(anuncio.getEncerradoEm()).isNull();
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoAnuncioAindaNoFuturo_quandoExecuta_entaoNaoExpira() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, AGORA.plusSeconds(60));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));

        assertThat(useCase.executar(ANUNCIO_ID, AGORA)).isFalse();

        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoAnuncioJaEncerrado_quandoExecuta_entaoNaoReprocessaNemPublicaDeNovo() {
        Anuncio anuncio = anuncio(StatusAnuncio.ENCERRADO, AGORA.minusSeconds(60));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));

        assertThat(useCase.executar(ANUNCIO_ID, AGORA)).isFalse();

        assertThat(anuncio.getEncerradoEm()).isNull();
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoAnuncioInexistente_quandoExecuta_entaoNaoFazNada() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThat(useCase.executar(ANUNCIO_ID, AGORA)).isFalse();

        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }
}
