package com.limio.api.comum.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.CRC32;

import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageWriterSpi;

import org.junit.jupiter.api.Test;

import com.limio.api.comum.midia.ImagemHelper.EscritorIndisponivelException;
import com.limio.api.comum.midia.ImagemHelper.ImagemProcessada;

class ImagemHelperTest {

    @Test
    void dadoJpegValido_quandoProcessa_entaoRegravaComoJpegComAsMesmasDimensoes() {
        ImagemProcessada imagem = ImagemHelper.processar(ImagensDeTeste.jpeg(40, 20)).orElseThrow();

        assertThat(imagem.mime()).isEqualTo("image/jpeg");
        assertThat(imagem.extensao()).isEqualTo("jpg");
        assertThat(imagem.largura()).isEqualTo(40);
        assertThat(imagem.altura()).isEqualTo(20);
        assertThat(ImagensDeTeste.ler(imagem.conteudo()).getWidth()).isEqualTo(40);
    }

    @Test
    void dadoPngComTransparencia_quandoProcessa_entaoContinuaPngComCanalAlfa() {
        ImagemProcessada imagem = ImagemHelper.processar(ImagensDeTeste.png(30, 30)).orElseThrow();

        assertThat(imagem.mime()).isEqualTo("image/png");
        assertThat(imagem.extensao()).isEqualTo("png");
        assertThat(ImagensDeTeste.ler(imagem.conteudo()).getColorModel().hasAlpha()).isTrue();
    }

    @Test
    void dadoJpegComExif_quandoProcessa_entaoNenhumMetadadoSobrevive() {
        byte[] original = ImagensDeTeste.jpegComExif(40, 20, 1);
        assertThat(ImagensDeTeste.contem(original, ImagensDeTeste.SEGREDO_EXIF)).isTrue();

        ImagemProcessada imagem = ImagemHelper.processar(original).orElseThrow();

        assertThat(ImagensDeTeste.contem(imagem.conteudo(), ImagensDeTeste.SEGREDO_EXIF)).isFalse();
        assertThat(ImagensDeTeste.contem(imagem.conteudo(), "Exif")).isFalse();
    }

    @Test
    void dadoOrientacao6_quandoProcessa_entaoGira90GrausNoSentidoHorario() {
        ImagemProcessada imagem = ImagemHelper.processar(ImagensDeTeste.jpegComExif(40, 20, 6)).orElseThrow();

        assertThat(imagem.largura()).isEqualTo(20);
        assertThat(imagem.altura()).isEqualTo(40);
        BufferedImage pronta = ImagensDeTeste.ler(imagem.conteudo());
        // A metade esquerda (vermelha) do original vai para cima.
        assertThat(ehVermelho(pronta.getRGB(10, 5))).isTrue();
        assertThat(ehAzul(pronta.getRGB(10, 35))).isTrue();
    }

    @Test
    void dadoOrientacao8_quandoProcessa_entaoGira90GrausNoSentidoAntiHorario() {
        ImagemProcessada imagem = ImagemHelper.processar(ImagensDeTeste.jpegComExif(40, 20, 8)).orElseThrow();

        BufferedImage pronta = ImagensDeTeste.ler(imagem.conteudo());
        assertThat(pronta.getWidth()).isEqualTo(20);
        // A metade esquerda (vermelha) do original vai para baixo.
        assertThat(ehAzul(pronta.getRGB(10, 5))).isTrue();
        assertThat(ehVermelho(pronta.getRGB(10, 35))).isTrue();
    }

    @Test
    void dadoFotoMaiorQueOLadoMaximo_quandoProcessa_entaoReduzMantendoAProporcao() {
        ImagemProcessada imagem = ImagemHelper.processar(ImagensDeTeste.jpeg(4100, 1000)).orElseThrow();

        assertThat(imagem.largura()).isEqualTo(ImagemHelper.LADO_MAXIMO);
        assertThat(imagem.altura()).isBetween(498, 501);
    }

    @Test
    void dadoGif_quandoProcessa_entaoRecusa() {
        assertThat(ImagemHelper.processar(ImagensDeTeste.gif(20, 20))).isEmpty();
    }

    @Test
    void dadoArquivoQueNaoEImagem_quandoProcessa_entaoRecusa() {
        assertThat(ImagemHelper.processar("não sou uma foto".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImagemHelper.processar(new byte[0])).isEmpty();
        assertThat(ImagemHelper.processar(null)).isEmpty();
    }

    @Test
    void dadoJpegTruncado_quandoProcessa_entaoRecusa() {
        byte[] jpeg = ImagensDeTeste.jpeg(40, 20);

        assertThat(ImagemHelper.processar(Arrays.copyOf(jpeg, 30))).isEmpty();
    }

    @Test
    void dadoPngQueDeclaraDimensoesAbsurdas_quandoProcessa_entaoRecusaSemDecodificar() {
        assertThat(ImagemHelper.processar(pngSoComCabecalho(20_000, 20_000))).isEmpty();
    }

    @Test
    void dadoRuntimeSemWriterJpeg_quandoProcessa_entaoFalhaComoErroDoServidorENaoComoFotoInvalida() {
        byte[] jpeg = ImagensDeTeste.jpeg(40, 20); // antes de tirar o writer: o próprio gerador usa ImageIO
        IIORegistry registro = IIORegistry.getDefaultInstance();
        List<ImageWriterSpi> removidos = new ArrayList<>();
        Iterator<ImageWriterSpi> escritores = registro.getServiceProviders(ImageWriterSpi.class,
                spi -> Arrays.asList(((ImageWriterSpi) spi).getFormatNames()).contains("jpeg"), false);
        escritores.forEachRemaining(removidos::add);
        removidos.forEach(spi -> registro.deregisterServiceProvider(spi, ImageWriterSpi.class));
        try {
            assertThatThrownBy(() -> ImagemHelper.processar(jpeg))
                    .isInstanceOf(EscritorIndisponivelException.class);
        } finally {
            removidos.forEach(spi -> registro.registerServiceProvider(spi, ImageWriterSpi.class));
        }
    }

    private static boolean ehVermelho(int rgb) {
        Color cor = new Color(rgb);
        return cor.getRed() > 200 && cor.getBlue() < 60;
    }

    private static boolean ehAzul(int rgb) {
        Color cor = new Color(rgb);
        return cor.getBlue() > 200 && cor.getRed() < 60;
    }

    /** Assinatura PNG + IHDR válido, sem dados de imagem: o tamanho vem só do cabeçalho. */
    private static byte[] pngSoComCabecalho(int largura, int altura) {
        ByteBuffer ihdr = ByteBuffer.allocate(4 + 13);
        ihdr.put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(largura).putInt(altura)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        ByteBuffer png = ByteBuffer.allocate(8 + 4 + ihdr.capacity() + 4);
        png.put(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' });
        png.putInt(13).put(ihdr.array()).putInt((int) crc.getValue());
        return png.array();
    }
}
