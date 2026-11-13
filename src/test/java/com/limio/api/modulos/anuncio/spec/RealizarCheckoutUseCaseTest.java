package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.util.unit.DataSize;

import com.limio.api.comum.eventos.CheckoutEnviadoEvent;
import com.limio.api.comum.excecao.enums.CodigoErro;
import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.ImagensDeTeste;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.enums.TipoMidia;
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.comum.midia.excecao.FotoInvalidaException;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.RealizarCheckoutUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.eventos.FotoAnuncioEnviadaEvent;
import com.limio.api.modulos.anuncio.excecao.FotoObrigatoriaException;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class RealizarCheckoutUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();

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

    @Mock
    private TravaTransacional trava;

    private RealizarCheckoutUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new RealizarCheckoutUseCase(anuncioService, aplicacaoService, midiaService, armazenamentoService,
                eventos, trava, DataSize.ofMegabytes(5), 5);
    }

    private Anuncio anuncio(StatusAnuncio status) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .prazoValidacaoHoras(24)
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    private Aplicacao candidatura(StatusAplicacao status) {
        return Aplicacao.builder().anuncioId(ANUNCIO_ID).prestadorId(PRESTADOR_ID).status(status)
                .dataAplicacao(Instant.now()).build();
    }

    private void comAnuncio(Anuncio anuncio) {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
    }

    private void comCandidatura(Aplicacao aplicacao) {
        when(aplicacaoService.buscarDoPrestadorNoAnuncio(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(Optional.of(aplicacao));
    }

    private static List<byte[]> umaFoto() {
        return List.of(ImagensDeTeste.jpeg(40, 30));
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO" })
    void dadoPrestadorEscolhido_quandoFazCheckout_entaoAguardaValidacaoGravaEvidenciaEPublicaEvento(
            StatusAnuncio status) {
        Anuncio anuncio = anuncio(status);
        Aplicacao aplicacao = candidatura(StatusAplicacao.ESCOLHIDA);
        comAnuncio(anuncio);
        comCandidatura(aplicacao);
        when(midiaService.salvar(any(Midia.class))).thenAnswer(i -> i.getArgument(0));
        when(anuncioService.salvar(anuncio)).thenReturn(anuncio);

        Anuncio resultado = useCase.executar(ANUNCIO_ID, PRESTADOR_ID, umaFoto(), "  Pronto.  ");

        assertThat(resultado.getStatus()).isEqualTo(StatusAnuncio.AGUARDANDO_VALIDACAO);
        assertThat(resultado.getCheckoutEm()).isNotNull();
        assertThat(resultado.getObservacaoCheckout()).isEqualTo("Pronto.");
        assertThat(aplicacao.getStatus()).isEqualTo(StatusAplicacao.AGUARDANDO_VALIDACAO);
        verify(aplicacaoService).salvar(aplicacao);

        ArgumentCaptor<Midia> midia = ArgumentCaptor.forClass(Midia.class);
        verify(midiaService).salvar(midia.capture());
        assertThat(midia.getValue().getTipo()).isEqualTo(TipoMidia.EVIDENCIA_CHECKOUT);
        assertThat(midia.getValue().getAnuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(midia.getValue().getUsuarioId()).isEqualTo(PRESTADOR_ID);
        assertThat(midia.getValue().getUrlS3()).startsWith("fotos-checkout/" + ANUNCIO_ID + "/");

        ArgumentCaptor<CheckoutEnviadoEvent> evento = ArgumentCaptor.forClass(CheckoutEnviadoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().anuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(evento.getValue().contratanteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().prestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(evento.getValue().anuncioTitulo()).isEqualTo("Instalar 3 tomadas");
    }

    @Test
    void dadoUploadFeito_quandoFazCheckout_entaoPublicaEventoDeLimpezaLogoDepoisDoUpload() {
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO);
        comAnuncio(anuncio);
        comCandidatura(candidatura(StatusAplicacao.ESCOLHIDA));
        when(midiaService.salvar(any(Midia.class))).thenAnswer(i -> i.getArgument(0));

        useCase.executar(ANUNCIO_ID, PRESTADOR_ID, umaFoto(), null);

        var ordem = inOrder(armazenamentoService, eventos);
        ordem.verify(armazenamentoService).enviar(startsWith("fotos-checkout/"), any(), anyString());
        ordem.verify(eventos).publishEvent(any(FotoAnuncioEnviadaEvent.class));
        ordem.verify(eventos).publishEvent(any(CheckoutEnviadoEvent.class));
    }

    @Test
    void dadoCheckout_quandoExecuta_entaoTravaOAnuncioAntesDeLer() {
        comAnuncio(anuncio(StatusAnuncio.EM_ANDAMENTO));
        comCandidatura(candidatura(StatusAplicacao.ESCOLHIDA));
        when(midiaService.salvar(any(Midia.class))).thenAnswer(i -> i.getArgument(0));

        useCase.executar(ANUNCIO_ID, PRESTADOR_ID, umaFoto(), null);

        var ordem = inOrder(trava, anuncioService);
        ordem.verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());
        ordem.verify(anuncioService).buscarPorId(ANUNCIO_ID);
    }

    @Test
    void dadoAnuncioInexistente_quandoFazCheckout_entao404SemNadaGravado() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, umaFoto(), null))
                .isInstanceOf(ServicoNaoEncontradoException.class);

        verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());

        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void dadoPrestadorSemCandidatura_quandoFazCheckout_entao404MesmoSemFoto() {
        comAnuncio(anuncio(StatusAnuncio.EM_ANDAMENTO));
        when(aplicacaoService.buscarDoPrestadorNoAnuncio(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, List.of(), null))
                .isInstanceOf(ServicoNaoEncontradoException.class);

        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @ParameterizedTest
    @EnumSource(value = StatusAplicacao.class, names = { "ENVIADA", "RECUSADA", "CONCLUIDA", "ENCERRADA" })
    void dadoCandidaturaNaoEscolhida_quandoFazCheckout_entao404(StatusAplicacao status) {
        comAnuncio(anuncio(StatusAnuncio.EM_ANDAMENTO));
        comCandidatura(candidatura(status));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, umaFoto(), null))
                .isInstanceOf(ServicoNaoEncontradoException.class);

        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "AGUARDANDO_VALIDACAO", "CONCLUIDO", "ENCERRADO" })
    void dadoAnuncioEmEstadoInvalido_quandoFazCheckout_entao409SemNadaGravado(StatusAnuncio status) {
        comAnuncio(anuncio(status));
        comCandidatura(candidatura(StatusAplicacao.ESCOLHIDA));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, umaFoto(), null))
                .isInstanceOf(ServicoEstadoInvalidoException.class);

        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void dadoNenhumaFoto_quandoFazCheckout_entaoFotoObrigatoriaSemNadaGravado() {
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO);
        comAnuncio(anuncio);
        comCandidatura(candidatura(StatusAplicacao.ESCOLHIDA));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, List.of(), null))
                .isInstanceOf(FotoObrigatoriaException.class)
                .extracting("codigo").isEqualTo(CodigoErro.FOTO_OBRIGATORIA);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.EM_ANDAMENTO);
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void dadoFotoQueNaoEImagem_quandoFazCheckout_entaoFotoInvalidaSemNenhumUpload() {
        comAnuncio(anuncio(StatusAnuncio.EM_ANDAMENTO));
        comCandidatura(candidatura(StatusAplicacao.ESCOLHIDA));
        List<byte[]> arquivos = List.of(ImagensDeTeste.jpeg(10, 10), ImagensDeTeste.gif(10, 10));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, arquivos, null))
                .isInstanceOf(FotoInvalidaException.class);

        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
        verify(midiaService, never()).salvar(any(Midia.class));
    }

    @Test
    void dadoFotoMaiorQueOLimite_quandoFazCheckout_entaoFotoInvalida() {
        comAnuncio(anuncio(StatusAnuncio.EM_ANDAMENTO));
        comCandidatura(candidatura(StatusAplicacao.ESCOLHIDA));
        List<byte[]> arquivos = List.of(new byte[5 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, arquivos, null))
                .isInstanceOf(FotoInvalidaException.class);
    }

    @Test
    void dadoMaisFotosQueOMaximo_quandoFazCheckout_entaoFotoInvalida() {
        comAnuncio(anuncio(StatusAnuncio.EM_ANDAMENTO));
        comCandidatura(candidatura(StatusAplicacao.ESCOLHIDA));
        byte[] foto = ImagensDeTeste.jpeg(10, 10);
        List<byte[]> arquivos = List.of(foto, foto, foto, foto, foto, foto);

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, arquivos, null))
                .isInstanceOf(FotoInvalidaException.class);

        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
    }

    @Test
    void dadoStorageForaDoAr_quandoFazCheckout_entaoPropagaEAnuncioNaoMuda() {
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO);
        Aplicacao aplicacao = candidatura(StatusAplicacao.ESCOLHIDA);
        comAnuncio(anuncio);
        comCandidatura(aplicacao);
        doThrow(new ArmazenamentoIndisponivelException()).when(armazenamentoService)
                .enviar(anyString(), any(), anyString());

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, PRESTADOR_ID, umaFoto(), null))
                .isInstanceOf(ArmazenamentoIndisponivelException.class);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.EM_ANDAMENTO);
        assertThat(aplicacao.getStatus()).isEqualTo(StatusAplicacao.ESCOLHIDA);
        verify(eventos, never()).publishEvent(any(CheckoutEnviadoEvent.class));
    }

    @Test
    void dadasDuasFotos_quandoFazCheckout_entaoUmaEvidenciaParaCadaEUmSoEvento() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        comAnuncio(anuncio);
        comCandidatura(candidatura(StatusAplicacao.ESCOLHIDA));
        when(midiaService.salvar(any(Midia.class))).thenAnswer(i -> i.getArgument(0));

        useCase.executar(ANUNCIO_ID, PRESTADOR_ID, List.of(ImagensDeTeste.jpeg(10, 10), ImagensDeTeste.png(10, 10)),
                null);

        verify(midiaService, times(2)).salvar(any(Midia.class));
        verify(eventos, times(1)).publishEvent(any(CheckoutEnviadoEvent.class));
    }
}
