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

import com.limio.api.comum.eventos.ServicoConcluidoEvent;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.ValidarServicoUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.ModoValidacao;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.ValidacaoSemCheckoutException;

@ExtendWith(MockitoExtension.class)
class ValidarServicoUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ApplicationEventPublisher eventos;

    private ValidarServicoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ValidarServicoUseCase(anuncioService, aplicacaoService, eventos);
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

    private Aplicacao escolhida() {
        return Aplicacao.builder()
                .anuncioId(ANUNCIO_ID)
                .prestadorId(PRESTADOR_ID)
                .status(StatusAplicacao.ESCOLHIDA)
                .dataAplicacao(Instant.now())
                .escolhidaEm(Instant.now())
                .build();
    }

    private void comAnuncioEEscolhida(Anuncio anuncio) {
        when(anuncioService.buscarParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));
    }

    @Test
    void dadoPresencialDePublicado_quandoValida_entaoConcluiEPublicaEvento() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        comAnuncioEEscolhida(anuncio);
        when(anuncioService.salvar(anuncio)).thenReturn(anuncio);

        Anuncio validado = useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, ModoValidacao.PRESENCIAL);

        assertThat(validado.getStatus()).isEqualTo(StatusAnuncio.CONCLUIDO);
        assertThat(validado.getValidadoEm()).isNotNull();

        ArgumentCaptor<ServicoConcluidoEvent> evento = ArgumentCaptor.forClass(ServicoConcluidoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().anuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(evento.getValue().anuncioTitulo()).isEqualTo("Instalar 3 tomadas");
        assertThat(evento.getValue().contratanteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().prestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(evento.getValue().ocorridoEm()).isEqualTo(validado.getValidadoEm());
    }

    @Test
    void dadoPresencialDeEmAndamento_quandoValida_entaoConclui() {
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO);
        comAnuncioEEscolhida(anuncio);
        when(anuncioService.salvar(anuncio)).thenReturn(anuncio);

        Anuncio validado = useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, ModoValidacao.PRESENCIAL);

        assertThat(validado.getStatus()).isEqualTo(StatusAnuncio.CONCLUIDO);
    }

    @Test
    void dadoPelaFotoDeAguardandoValidacao_quandoValida_entaoConclui() {
        Anuncio anuncio = anuncio(StatusAnuncio.AGUARDANDO_VALIDACAO);
        comAnuncioEEscolhida(anuncio);
        when(anuncioService.salvar(anuncio)).thenReturn(anuncio);

        Anuncio validado = useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, ModoValidacao.PELA_FOTO);

        assertThat(validado.getStatus()).isEqualTo(StatusAnuncio.CONCLUIDO);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO" }, mode = EnumSource.Mode.EXCLUDE)
    void dadoPresencialForaDePublicadoOuEmAndamento_quandoValida_entaoRecusa(StatusAnuncio status) {
        Anuncio anuncio = anuncio(status);
        comAnuncioEEscolhida(anuncio);

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, ModoValidacao.PRESENCIAL))
                .isInstanceOf(ServicoEstadoInvalidoException.class);

        assertThat(anuncio.getStatus()).isEqualTo(status);
        assertThat(anuncio.getValidadoEm()).isNull();
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO" })
    void dadoPelaFotoAntesDoCheckout_quandoValida_entaoRecusaSemCheckout(StatusAnuncio status) {
        Anuncio anuncio = anuncio(status);
        comAnuncioEEscolhida(anuncio);

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, ModoValidacao.PELA_FOTO))
                .isInstanceOf(ValidacaoSemCheckoutException.class);

        assertThat(anuncio.getStatus()).isEqualTo(status);
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "CONCLUIDO", "ENCERRADO" })
    void dadoPelaFotoDeServicoJaFinalizado_quandoValida_entaoRecusaEstadoInvalido(StatusAnuncio status) {
        Anuncio anuncio = anuncio(status);
        comAnuncioEEscolhida(anuncio);

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, ModoValidacao.PELA_FOTO))
                .isInstanceOf(ServicoEstadoInvalidoException.class);

        assertThat(anuncio.getStatus()).isEqualTo(status);
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoQuemNaoEDono_quandoValida_entao404() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        when(anuncioService.buscarParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, UUID.randomUUID(), ModoValidacao.PRESENCIAL))
                .isInstanceOf(ServicoNaoEncontradoException.class);

        verifyNoInteractions(aplicacaoService, eventos);
    }

    @Test
    void dadoSemCandidaturaEscolhida_quandoValida_entao404() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        when(anuncioService.buscarParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, ModoValidacao.PRESENCIAL))
                .isInstanceOf(ServicoNaoEncontradoException.class);

        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoAnuncioInexistente_quandoValida_entao404() {
        when(anuncioService.buscarParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID, ModoValidacao.PRESENCIAL))
                .isInstanceOf(ServicoNaoEncontradoException.class);

        verifyNoInteractions(aplicacaoService, eventos);
    }
}
