package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.RemoverFotoAnuncioUseCase;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.eventos.FotoAnuncioRemovidaEvent;
import com.limio.api.modulos.anuncio.excecao.AnuncioEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.FotoNaoEncontradaException;

@ExtendWith(MockitoExtension.class)
class RemoverFotoAnuncioUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID DONO_ID = UUID.randomUUID();
    private static final UUID FOTO_ID = UUID.randomUUID();
    private static final String CHAVE = "fotos-anuncio/x/y.jpg";

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private MidiaService midiaService;

    @Mock
    private TravaTransacional trava;

    @Mock
    private ApplicationEventPublisher eventos;

    private RemoverFotoAnuncioUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new RemoverFotoAnuncioUseCase(anuncioService, aplicacaoService, midiaService, trava, eventos);
    }

    private void anuncio(StatusAnuncio status) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(DONO_ID)
                .categoriaId(UUID.randomUUID())
                .descricao("Faxina")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(2)))
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
    }

    private Midia foto() {
        Midia foto = Midia.fotoAnuncio(DONO_ID, ANUNCIO_ID, CHAVE, "image/jpeg", 10, 10, 100);
        ReflectionTestUtils.setField(foto, "id", FOTO_ID);
        return foto;
    }

    @Test
    void dadoFotoAtivaDoAnuncioEditavel_quandoRemove_entaoExpurgaESoPublicaEventoParaApagarOObjeto() {
        anuncio(StatusAnuncio.PUBLICADO);
        Midia foto = foto();
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);
        when(midiaService.buscarFotoAtivaDoAnuncio(ANUNCIO_ID, FOTO_ID)).thenReturn(Optional.of(foto));

        useCase.executar(DONO_ID, ANUNCIO_ID, FOTO_ID);

        assertThat(foto.getExpurgoEm()).isNotNull();
        verify(midiaService).salvarAgora(foto);
        verify(eventos).publishEvent(new FotoAnuncioRemovidaEvent(CHAVE));
    }

    @Test
    void dadoRemocao_quandoExecuta_entaoTravaAnuncioDepoisFotosAntesDeLerOEstado() {
        anuncio(StatusAnuncio.PUBLICADO);
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);
        when(midiaService.buscarFotoAtivaDoAnuncio(ANUNCIO_ID, FOTO_ID)).thenReturn(Optional.of(foto()));

        useCase.executar(DONO_ID, ANUNCIO_ID, FOTO_ID);

        InOrder ordem = inOrder(trava, anuncioService, midiaService, aplicacaoService);
        ordem.verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());
        ordem.verify(anuncioService).buscarPorId(ANUNCIO_ID);
        ordem.verify(midiaService).travarFotosAnuncio(ANUNCIO_ID);
        ordem.verify(aplicacaoService).existeEscolhida(ANUNCIO_ID);
        ordem.verify(midiaService).buscarFotoAtivaDoAnuncio(ANUNCIO_ID, FOTO_ID);
    }

    @Test
    void dadoCandidaturaEscolhida_quandoRemove_entaoRecusaSemTocarNaFoto() {
        anuncio(StatusAnuncio.PUBLICADO);
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID, FOTO_ID))
                .isInstanceOf(AnuncioEstadoInvalidoException.class);

        verify(midiaService, never()).salvarAgora(any());
        verifyNoInteractions(eventos);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = "PUBLICADO", mode = EnumSource.Mode.EXCLUDE)
    void dadoAnuncioForaDePublicado_quandoRemove_entaoRecusa(StatusAnuncio status) {
        anuncio(status);
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID, FOTO_ID))
                .isInstanceOf(AnuncioEstadoInvalidoException.class);

        verify(midiaService, never()).salvarAgora(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoAnuncioDeOutroUsuario_quandoRemove_entao404SemConferirEstadoNemFoto() {
        anuncio(StatusAnuncio.PUBLICADO);

        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), ANUNCIO_ID, FOTO_ID))
                .isInstanceOf(AnuncioNaoEncontradoException.class);

        verifyNoInteractions(aplicacaoService, eventos);
        verify(midiaService, never()).buscarFotoAtivaDoAnuncio(any(), any());
    }

    @Test
    void dadoAnuncioInexistente_quandoRemove_entao404() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID, FOTO_ID))
                .isInstanceOf(AnuncioNaoEncontradoException.class);
    }

    @Test
    void dadoFotoInexistenteJaRemovidaOuDeOutroAnuncio_quandoRemove_entao404() {
        anuncio(StatusAnuncio.PUBLICADO);
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);
        when(midiaService.buscarFotoAtivaDoAnuncio(ANUNCIO_ID, FOTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID, FOTO_ID))
                .isInstanceOf(FotoNaoEncontradaException.class);

        verify(midiaService, never()).salvarAgora(any());
        verifyNoInteractions(eventos);
    }
}
