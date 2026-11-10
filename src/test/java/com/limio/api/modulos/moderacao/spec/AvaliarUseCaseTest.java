package com.limio.api.modulos.moderacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import org.springframework.context.ApplicationEventPublisher;

import com.limio.api.comum.eventos.AvaliacaoPublicadaEvent;
import com.limio.api.comum.portas.ConsultaServico;
import com.limio.api.comum.portas.ResumoServico;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.moderacao.actions.usecase.AvaliarUseCase;
import com.limio.api.modulos.moderacao.avaliacao.Avaliacao;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoService;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacoesRecebidas;
import com.limio.api.modulos.moderacao.excecao.AvaliacaoDuplicadaException;
import com.limio.api.modulos.moderacao.excecao.ComentarioSemNotaException;
import com.limio.api.modulos.moderacao.excecao.JanelaAvaliacaoEncerradaException;
import com.limio.api.modulos.moderacao.excecao.NotaObrigatoriaException;
import com.limio.api.modulos.moderacao.excecao.ServicoNaoConcluidoException;
import com.limio.api.modulos.moderacao.excecao.ServicoNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class AvaliarUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-03T15:00:00Z");
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();

    @Mock
    private ConsultaServico consultaServico;

    @Mock
    private AvaliacaoService avaliacaoService;

    @Mock
    private ApplicationEventPublisher eventos;

    private AvaliarUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new AvaliarUseCase(consultaServico, avaliacaoService, eventos,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void servico(Instant concluidoEm) {
        when(consultaServico.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(
                new ResumoServico(ANUNCIO_ID, "Instalar 3 tomadas", CONTRATANTE_ID, PRESTADOR_ID, concluidoEm)));
    }

    @Test
    void empregadorAvaliaOPrestadorEPublicaOTotalRecalculado() {
        servico(AGORA.minus(Duration.ofDays(1)));
        when(avaliacaoService.salvarNova(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(avaliacaoService.recalcular(PRESTADOR_ID, PapelUsuario.PRESTADOR))
                .thenReturn(new AvaliacoesRecebidas(new BigDecimal("4.5"), 2));

        Avaliacao avaliacao = useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, 4, "  Caprichou  ");

        assertThat(avaliacao.getAvaliadoId()).isEqualTo(PRESTADOR_ID);
        assertThat(avaliacao.getPapelAvaliado()).isEqualTo(PapelUsuario.PRESTADOR);
        assertThat(avaliacao.getComentario()).isEqualTo("Caprichou");
        assertThat(avaliacao.getPublicadaEm()).isEqualTo(AGORA);
        verify(avaliacaoService).travarRecebidas(PRESTADOR_ID, PapelUsuario.PRESTADOR);
        ArgumentCaptor<AvaliacaoPublicadaEvent> evento = ArgumentCaptor.forClass(AvaliacaoPublicadaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isEqualTo(new AvaliacaoPublicadaEvent(PRESTADOR_ID, PapelUsuario.PRESTADOR,
                new BigDecimal("4.5"), 2, AGORA));
    }

    @Test
    void prestadorAvaliaOEmpregadorNoPapelDeEmpregador() {
        servico(AGORA.minus(Duration.ofDays(1)));
        when(avaliacaoService.salvarNova(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(avaliacaoService.recalcular(CONTRATANTE_ID, PapelUsuario.EMPREGADOR))
                .thenReturn(new AvaliacoesRecebidas(new BigDecimal("5.0"), 1));

        Avaliacao avaliacao = useCase.executar(ANUNCIO_ID, PRESTADOR_ID, 5, " ");

        assertThat(avaliacao.getAvaliadoId()).isEqualTo(CONTRATANTE_ID);
        assertThat(avaliacao.getPapelAvaliado()).isEqualTo(PapelUsuario.EMPREGADOR);
        assertThat(avaliacao.getComentario()).isNull();
    }

    @Test
    void comentarioSemNotaERecusadoAntesDeConsultarOServico() {
        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, null, "Ótimo"))
                .isInstanceOf(ComentarioSemNotaException.class);
        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, null, null))
                .isInstanceOf(NotaObrigatoriaException.class);
        verifyNoInteractions(consultaServico, avaliacaoService, eventos);
    }

    @Test
    void quemNaoEParteNaoEnxergaOServico() {
        servico(AGORA.minus(Duration.ofDays(1)));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, UUID.randomUUID(), 5, null))
                .isInstanceOf(ServicoNaoEncontradoException.class);
        verifyNoInteractions(avaliacaoService);
    }

    @Test
    void servicoNaoConcluidoERecusado() {
        servico(null);

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, 5, null))
                .isInstanceOf(ServicoNaoConcluidoException.class);
        verifyNoInteractions(avaliacaoService);
    }

    @Test
    void janelaFechaExatamente7DiasDepoisDaConclusao() {
        servico(AGORA.minus(Duration.ofDays(7)));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, 5, null))
                .isInstanceOf(JanelaAvaliacaoEncerradaException.class);
        verifyNoInteractions(avaliacaoService);
    }

    @Test
    void segundaAvaliacaoDoMesmoAvaliadorERecusada() {
        servico(AGORA.minus(Duration.ofDays(1)));
        when(avaliacaoService.jaAvaliou(ANUNCIO_ID, CONTRATANTE_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, 5, null))
                .isInstanceOf(AvaliacaoDuplicadaException.class);
        verify(avaliacaoService, never()).salvarNova(any());
        verifyNoInteractions(eventos);
    }
}
