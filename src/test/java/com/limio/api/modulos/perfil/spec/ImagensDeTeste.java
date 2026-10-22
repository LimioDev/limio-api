package com.limio.api.modulos.perfil.spec;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

/** Gera imagens reais em memória para os testes da foto de perfil (spec e story). */
public final class ImagensDeTeste {

    /** Texto gravado no EXIF dos JPEGs de teste: não pode aparecer no arquivo guardado. */
    public static final String SEGREDO_EXIF = "GPS -23.5505 -46.6333 casa da Maria";

    private ImagensDeTeste() {
    }

    public static byte[] jpeg(int largura, int altura) {
        return gravar(imagem(largura, altura, BufferedImage.TYPE_INT_RGB), "jpeg");
    }

    public static byte[] png(int largura, int altura) {
        return gravar(imagem(largura, altura, BufferedImage.TYPE_INT_ARGB), "png");
    }

    public static byte[] gif(int largura, int altura) {
        return gravar(imagem(largura, altura, BufferedImage.TYPE_INT_RGB), "gif");
    }

    /**
     * JPEG com um segmento EXIF (APP1) logo depois do SOI, contendo a tag Orientation e uma
     * ImageDescription com {@link #SEGREDO_EXIF} — faz o papel dos metadados de localização.
     */
    public static byte[] jpegComExif(int largura, int altura, int orientacao) {
        byte[] jpeg = jpeg(largura, altura);
        byte[] descricao = (SEGREDO_EXIF + "\0").getBytes(StandardCharsets.US_ASCII);

        // TIFF big-endian: cabeçalho (8) + IFD0 com 2 entradas (2 + 2*12 + 4) + texto da descrição.
        int inicioTexto = 8 + 2 + 2 * 12 + 4;
        ByteBuffer tiff = ByteBuffer.allocate(inicioTexto + descricao.length);
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 0x2A).putInt(8);
        tiff.putShort((short) 2);
        tiff.putShort((short) 0x010E).putShort((short) 2).putInt(descricao.length).putInt(inicioTexto);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientacao).putShort((short) 0);
        tiff.putInt(0);
        tiff.put(descricao);

        byte[] cabecalhoExif = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int tamanhoSegmento = 2 + cabecalhoExif.length + tiff.capacity();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(jpeg, 0, 2); // SOI
        saida.write(0xFF);
        saida.write(0xE1);
        saida.write(tamanhoSegmento >> 8);
        saida.write(tamanhoSegmento & 0xFF);
        saida.writeBytes(cabecalhoExif);
        saida.writeBytes(tiff.array());
        saida.write(jpeg, 2, jpeg.length - 2);
        return saida.toByteArray();
    }

    public static boolean contem(byte[] conteudo, String texto) {
        return new String(conteudo, StandardCharsets.ISO_8859_1).contains(texto);
    }

    public static BufferedImage ler(byte[] conteudo) {
        try {
            return ImageIO.read(new java.io.ByteArrayInputStream(conteudo));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Metade esquerda vermelha, direita azul: dá pra conferir pra onde a imagem girou. */
    private static BufferedImage imagem(int largura, int altura, int tipo) {
        BufferedImage imagem = new BufferedImage(largura, altura, tipo);
        Graphics2D g = imagem.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, largura / 2, altura);
        g.setColor(Color.BLUE);
        g.fillRect(largura / 2, 0, largura - largura / 2, altura);
        g.dispose();
        return imagem;
    }

    private static byte[] gravar(BufferedImage imagem, String formato) {
        try {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            if (!ImageIO.write(imagem, formato, saida))
                throw new IllegalStateException("sem writer para " + formato);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
