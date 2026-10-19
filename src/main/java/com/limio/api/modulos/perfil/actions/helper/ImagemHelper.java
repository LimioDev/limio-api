package com.limio.api.modulos.perfil.actions.helper;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Prepara a foto de perfil para guardar: identifica o formato pelo conteúdo (nunca pela extensão ou
 * pelo Content-Type do cliente), aplica a orientação do EXIF, reduz o lado maior a
 * {@link #LADO_MAXIMO} e regrava a imagem a partir dos pixels. A regravação descarta todo metadado
 * do original (EXIF, GPS, XMP, ICC). Utilitário puro, sem Spring.
 */
public final class ImagemHelper {

    /** Lado maior da foto guardada. Foto de perfil não precisa mais que isso, e limita a memória por upload. */
    public static final int LADO_MAXIMO = 2048;

    /** Acima disso nem decodifica: protege contra arquivo pequeno que declara dimensões absurdas. */
    static final long PIXELS_MAXIMOS = 100_000_000L;

    private static final float QUALIDADE_JPEG = 0.9f;

    private ImagemHelper() {
    }

    /** Imagem pronta para o storage. */
    public record ImagemProcessada(byte[] conteudo, String mime, String extensao, int largura, int altura) {
    }

    /** Vazio quando o conteúdo não é JPEG nem PNG legível, ou é grande demais para decodificar. */
    public static Optional<ImagemProcessada> processar(byte[] original) {
        if (original == null || original.length == 0)
            return Optional.empty();
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            if (entrada == null)
                return Optional.empty();
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext())
                return Optional.empty();
            ImageReader leitor = leitores.next();
            try {
                Formato formato = Formato.de(leitor.getFormatName());
                if (formato == null)
                    return Optional.empty();
                leitor.setInput(entrada, true, true);
                long largura = leitor.getWidth(0);
                long altura = leitor.getHeight(0);
                if (largura <= 0 || altura <= 0 || largura * altura > PIXELS_MAXIMOS)
                    return Optional.empty();

                ImageReadParam parametros = leitor.getDefaultReadParam();
                // Subamostragem já na leitura: uma foto de 48 MP não é decodificada inteira na memória.
                int passo = (int) Math.max(1, Math.max(largura, altura) / LADO_MAXIMO);
                parametros.setSourceSubsampling(passo, passo, 0, 0);
                BufferedImage lida = leitor.read(0, parametros);

                int orientacao = formato == Formato.JPEG ? OrientacaoExif.ler(original) : 1;
                BufferedImage pronta = redesenhar(lida, orientacao, formato.tipoPixel);
                return Optional.of(new ImagemProcessada(gravar(pronta, formato), formato.mime, formato.extensao,
                        pronta.getWidth(), pronta.getHeight()));
            } finally {
                leitor.dispose();
            }
        } catch (IOException | RuntimeException e) {
            // Arquivo truncado/corrompido, JPEG CMYK etc.: o ImageIO não lê, então não é foto aceitável.
            return Optional.empty();
        }
    }

    /** Aplica orientação + redução numa única passada, já no tipo de pixel do formato de saída. */
    private static BufferedImage redesenhar(BufferedImage origem, int orientacao, int tipoPixel) {
        int w = origem.getWidth();
        int h = origem.getHeight();
        boolean girada = orientacao >= 5 && orientacao <= 8;
        int larguraFinal = girada ? h : w;
        int alturaFinal = girada ? w : h;
        double escala = Math.min(1.0, (double) LADO_MAXIMO / Math.max(larguraFinal, alturaFinal));
        int larguraSaida = Math.max(1, (int) Math.round(larguraFinal * escala));
        int alturaSaida = Math.max(1, (int) Math.round(alturaFinal * escala));

        AffineTransform transformacao = AffineTransform.getScaleInstance(
                (double) larguraSaida / larguraFinal, (double) alturaSaida / alturaFinal);
        transformacao.concatenate(orientar(orientacao, w, h));

        BufferedImage destino = new BufferedImage(larguraSaida, alturaSaida, tipoPixel);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (tipoPixel == BufferedImage.TYPE_INT_RGB) {
                // JPEG não tem transparência: fundo branco em vez de preto onde o original era transparente.
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, larguraSaida, alturaSaida);
            }
            g.drawImage(origem, transformacao, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    /** Transformação que leva a imagem gravada para a orientação de exibição (tag EXIF 0x0112). */
    static AffineTransform orientar(int orientacao, int w, int h) {
        return switch (orientacao) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
            default -> new AffineTransform();
        };
    }

    private static byte[] gravar(BufferedImage imagem, Formato formato) throws IOException {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName(formato.nomeImageIo).next();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ImageOutputStream destino = ImageIO.createImageOutputStream(saida)) {
            escritor.setOutput(destino);
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            if (formato == Formato.JPEG) {
                parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parametros.setCompressionQuality(QUALIDADE_JPEG);
            }
            // Metadados nulos: o writer grava só o mínimo do formato (JFIF/IHDR), nada do original.
            escritor.write(null, new IIOImage(imagem, null, null), parametros);
        } finally {
            escritor.dispose();
        }
        return saida.toByteArray();
    }

    private enum Formato {
        JPEG("jpeg", "image/jpeg", "jpg", BufferedImage.TYPE_INT_RGB),
        PNG("png", "image/png", "png", BufferedImage.TYPE_INT_ARGB);

        final String nomeImageIo;
        final String mime;
        final String extensao;
        final int tipoPixel;

        Formato(String nomeImageIo, String mime, String extensao, int tipoPixel) {
            this.nomeImageIo = nomeImageIo;
            this.mime = mime;
            this.extensao = extensao;
            this.tipoPixel = tipoPixel;
        }

        static Formato de(String nomeDoLeitor) {
            return switch (nomeDoLeitor.toLowerCase(Locale.ROOT)) {
                case "jpeg", "jpg" -> JPEG;
                case "png" -> PNG;
                default -> null;
            };
        }
    }

    /**
     * Lê só a tag Orientation (0x0112) do IFD0 do EXIF de um JPEG. Qualquer estrutura inesperada
     * devolve 1 (sem rotação): na dúvida, a foto sai como foi gravada, nunca falha por isso.
     */
    static final class OrientacaoExif {

        private OrientacaoExif() {
        }

        static int ler(byte[] jpeg) {
            try {
                int i = 2; // depois do SOI (FFD8)
                while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
                    int marcador = jpeg[i + 1] & 0xFF;
                    if (marcador == 0xDA || marcador == 0xD9) // início dos dados da imagem / fim
                        return 1;
                    int tamanho = u16(jpeg, i + 2, false);
                    if (marcador == 0xE1 && tamanho >= 8 && ehExif(jpeg, i + 4))
                        return orientacaoNoTiff(jpeg, i + 10, i + 2 + tamanho);
                    i += 2 + tamanho;
                }
            } catch (IndexOutOfBoundsException e) {
                // segmento truncado: trata como sem orientação
            }
            return 1;
        }

        private static boolean ehExif(byte[] b, int i) {
            return b[i] == 'E' && b[i + 1] == 'x' && b[i + 2] == 'i' && b[i + 3] == 'f' && b[i + 4] == 0
                    && b[i + 5] == 0;
        }

        private static int orientacaoNoTiff(byte[] b, int tiff, int fim) {
            boolean littleEndian;
            if (b[tiff] == 'I' && b[tiff + 1] == 'I')
                littleEndian = true;
            else if (b[tiff] == 'M' && b[tiff + 1] == 'M')
                littleEndian = false;
            else
                return 1;
            int ifd0 = tiff + u32(b, tiff + 4, littleEndian);
            int entradas = u16(b, ifd0, littleEndian);
            for (int n = 0; n < entradas; n++) {
                int entrada = ifd0 + 2 + n * 12;
                if (entrada + 12 > fim)
                    return 1;
                if (u16(b, entrada, littleEndian) == 0x0112) {
                    int valor = u16(b, entrada + 8, littleEndian);
                    return valor >= 1 && valor <= 8 ? valor : 1;
                }
            }
            return 1;
        }

        private static int u16(byte[] b, int i, boolean littleEndian) {
            int a = b[i] & 0xFF;
            int c = b[i + 1] & 0xFF;
            return littleEndian ? (c << 8) | a : (a << 8) | c;
        }

        private static int u32(byte[] b, int i, boolean littleEndian) {
            return littleEndian
                    ? (u16(b, i + 2, true) << 16) | u16(b, i, true)
                    : (u16(b, i, false) << 16) | u16(b, i + 2, false);
        }
    }
}
