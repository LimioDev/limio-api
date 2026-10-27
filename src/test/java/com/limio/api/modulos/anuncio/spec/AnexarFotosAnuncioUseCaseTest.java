package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
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
import org.springframework.util.unit.DataSize;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.ImagensDeTeste;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.enums.TipoMidia;
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.comum.midia.excecao.FotoInvalidaException;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.FotoDoAnuncio;
import com.limio.api.modulos.anuncio.actions.usecase.AnexarFotosAnuncioUseCase;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.eventos.FotoAnuncioEnviadaEvent;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.FotosNaoPermitidasException;

@ExtendWith(MockitoExtension.class)
class AnexarFotosAnuncioUseCaseTest {

    private static final UUID DONO_ID = UUID.randomUUID();
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final int QUANTIDADE_MAXIMA = 5;
    private static final LinkTemporario LINK = new LinkTemporario("https://bucket/assinado",
            Instant.parse("2026-10-27T18:00:00Z"));

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private MidiaService midiaService;

    @Mock
    private ArmazenamentoService armazenamentoService;

    @Mock
    private ApplicationEventPublisher eventos;

    private AnexarFotosAnuncioUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new AnexarFotosAnuncioUseCase(anuncioService, aplicacaoService, midiaService, armazenamentoService,
                eventos, DataSize.ofMegabytes(5), QUANTIDADE_MAXIMA);
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

    private void publicadoSemEscolhidoComFotos(int fotosJaGravadas) {
        anuncio(StatusAnuncio.PUBLICADO);
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);
        List<Midia> gravadas = Collections.nCopies(fotosJaGravadas,
                Midia.fotoAnuncio(DONO_ID, ANUNCIO_ID, "fotos-anuncio/x.jpg", "image/jpeg", 10, 10, 100));
        when(midiaService.buscarFotosAtivasDoAnuncio(ANUNCIO_ID)).thenReturn(gravadas);
    }

    private void storageResponde() {
        when(midiaService.salvar(any(Midia.class))).thenAnswer(chamada -> chamada.getArgument(0));
        when(armazenamentoService.gerarLink(anyString())).thenReturn(LINK);
    }

    @Test
    void dadoDuasFotosValidas_quandoAnexa_entaoGuardaSemExifEGravaMidiaDoAnuncio() {
        publicadoSemEscolhidoComFotos(0);
        storageResponde();

        List<FotoDoAnuncio> fotos = useCase.executar(DONO_ID, ANUNCIO_ID,
                List.of(ImagensDeTeste.jpegComExif(40, 20, 1), ImagensDeTeste.png(30, 30)));

        assertThat(fotos).hasSize(2).allSatisfy(foto -> assertThat(foto.link()).isEqualTo(LINK));
        ArgumentCaptor<String> chave = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> conteudo = ArgumentCaptor.forClass(byte[].class);
        verify(armazenamentoService, times(2)).enviar(chave.capture(), conteudo.capture(), anyString());
        assertThat(chave.getAllValues()).allSatisfy(
                valor -> assertThat(valor).startsWith("fotos-anuncio/" + ANUNCIO_ID + "/"));
        assertThat(ImagensDeTeste.contem(conteudo.getAllValues().get(0), ImagensDeTeste.SEGREDO_EXIF)).isFalse();

        ArgumentCaptor<Midia> midia = ArgumentCaptor.forClass(Midia.class);
        verify(midiaService, times(2)).salvar(midia.capture());
        assertThat(midia.getAllValues()).allSatisfy(gravada -> {
            assertThat(gravada.getTipo()).isEqualTo(TipoMidia.FOTO_ANUNCIO);
            assertThat(gravada.getAnuncioId()).isEqualTo(ANUNCIO_ID);
            assertThat(gravada.getUsuarioId()).isEqualTo(DONO_ID);
            assertThat(gravada.isExifRemovido()).isTrue();
        });
        verify(eventos).publishEvent(new FotoAnuncioEnviadaEvent(chave.getAllValues().get(0)));
        verify(eventos).publishEvent(new FotoAnuncioEnviadaEvent(chave.getAllValues().get(1)));
    }

    @Test
    void dadoEnvio_quandoAnexa_entaoTravaAntesDeConferirEstadoELimite() {
        publicadoSemEscolhidoComFotos(0);
        storageResponde();

        useCase.executar(DONO_ID, ANUNCIO_ID, List.of(ImagensDeTeste.png(30, 30)));

        InOrder ordem = inOrder(midiaService, aplicacaoService, armazenamentoService);
        ordem.verify(midiaService).travarFotosAnuncio(ANUNCIO_ID);
        ordem.verify(aplicacaoService).existeEscolhida(ANUNCIO_ID);
        ordem.verify(midiaService).buscarFotosAtivasDoAnuncio(ANUNCIO_ID);
        ordem.verify(armazenamentoService).enviar(anyString(), any(), eq("image/png"));
    }

    @Test
    void dadoAnuncioDeOutraPessoa_quandoAnexa_entao404SemTravarNemEnviar() {
        anuncio(StatusAnuncio.PUBLICADO);

        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), ANUNCIO_ID, List.of(ImagensDeTeste.png(5, 5))))
                .isInstanceOf(AnuncioNaoEncontradoException.class);
        verify(midiaService, never()).travarFotosAnuncio(any());
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
    }

    @Test
    void dadoAnuncioInexistente_quandoAnexa_entao404() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID, List.of(ImagensDeTeste.png(5, 5))))
                .isInstanceOf(AnuncioNaoEncontradoException.class);
    }

    @Test
    void dadoCandidaturaEscolhida_quandoAnexa_entaoRecusaSemEnviar() {
        anuncio(StatusAnuncio.PUBLICADO);
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID, List.of(ImagensDeTeste.png(5, 5))))
                .isInstanceOf(FotosNaoPermitidasException.class);
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
    }

    @Test
    void dadoAnuncioEmAndamento_quandoAnexa_entaoRecusa() {
        anuncio(StatusAnuncio.EM_ANDAMENTO);

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID, List.of(ImagensDeTeste.png(5, 5))))
                .isInstanceOf(FotosNaoPermitidasException.class);
    }

    @Test
    void dadoLimiteJaQuaseCheio_quandoAnexaMaisDoQueCabe_entaoRecusaSemEnviarNenhuma() {
        publicadoSemEscolhidoComFotos(QUANTIDADE_MAXIMA - 1);

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID,
                List.of(ImagensDeTeste.png(5, 5), ImagensDeTeste.png(5, 5))))
                .isInstanceOf(FotoInvalidaException.class)
                .hasMessageContaining(String.valueOf(QUANTIDADE_MAXIMA));
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
    }

    @Test
    void dadoUmaFotoInvalidaNoMeioDoEnvio_quandoAnexa_entaoRecusaTudoAntesDoPrimeiroUpload() {
        publicadoSemEscolhidoComFotos(0);

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID,
                List.of(ImagensDeTeste.png(5, 5), ImagensDeTeste.gif(5, 5))))
                .isInstanceOf(FotoInvalidaException.class);
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
    }

    @Test
    void dadoNenhumArquivo_quandoAnexa_entaoRecusaComoVazia() {
        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID, List.of()))
                .isInstanceOf(FotoInvalidaException.class);
    }

    @Test
    void dadoStorageFalhandoNaSegundaFoto_quandoAnexa_entaoAPrimeiraJaFicouMarcadaPraLimpeza() {
        publicadoSemEscolhidoComFotos(0);
        storageResponde();
        doNothing().doThrow(new ArmazenamentoIndisponivelException())
                .when(armazenamentoService).enviar(anyString(), any(), anyString());

        assertThatThrownBy(() -> useCase.executar(DONO_ID, ANUNCIO_ID,
                List.of(ImagensDeTeste.png(5, 5), ImagensDeTeste.png(6, 6))))
                .isInstanceOf(ArmazenamentoIndisponivelException.class);
        // Só a primeira subiu: é ela que o rollback precisa apagar do bucket.
        ArgumentCaptor<String> chave = ArgumentCaptor.forClass(String.class);
        verify(armazenamentoService, times(2)).enviar(chave.capture(), any(), anyString());
        verify(eventos, times(1)).publishEvent(any(FotoAnuncioEnviadaEvent.class));
        verify(eventos).publishEvent(new FotoAnuncioEnviadaEvent(chave.getAllValues().get(0)));
    }
}
