package com.limio.api.comum.midia;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.comum.midia.enums.TipoMidia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Arquivo guardado no storage. {@code urlS3} é a chave do objeto no bucket (privado), não uma URL
 * pública. Imutável depois de criada, exceto o expurgo.
 *
 * Fica em {@code comum/} porque dois módulos gravam mídia: {@code perfil} (foto de perfil, TICKET-0056) e
 * {@code anuncio} (fotos do anúncio, ETI-59).
 */
@Entity
@Table(name = "midia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Midia extends BaseEntity {

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TipoMidia tipo;

    /** Anúncio da foto, só em {@code FOTO_ANUNCIO}. Coluna crua, sem FK pra {@code anuncio} (ADR-0001 §1.6). */
    @Column(name = "anuncio_id", updatable = false)
    private UUID anuncioId;

    @Column(name = "url_s3", nullable = false, updatable = false, unique = true, length = 500)
    private String urlS3;

    @Column(nullable = false, updatable = false, length = 50)
    private String mime;

    @Column(nullable = false, updatable = false)
    private int largura;

    @Column(nullable = false, updatable = false)
    private int altura;

    @Column(nullable = false, updatable = false)
    private long bytes;

    @Column(name = "exif_removido", nullable = false, updatable = false)
    private boolean exifRemovido;

    @Column(name = "expurgo_em")
    private Instant expurgoEm;

    public static Midia fotoPerfil(UUID usuarioId, String urlS3, String mime, int largura, int altura, long bytes) {
        return foto(TipoMidia.FOTO_PERFIL, usuarioId, null, urlS3, mime, largura, altura, bytes);
    }

    /** Foto de um anúncio; {@code usuarioId} é o dono do anúncio, quem enviou. */
    public static Midia fotoAnuncio(UUID usuarioId, UUID anuncioId, String urlS3, String mime, int largura, int altura,
            long bytes) {
        return foto(TipoMidia.FOTO_ANUNCIO, usuarioId, anuncioId, urlS3, mime, largura, altura, bytes);
    }

    private static Midia foto(TipoMidia tipo, UUID usuarioId, UUID anuncioId, String urlS3, String mime, int largura,
            int altura, long bytes) {
        Midia midia = new Midia();
        midia.usuarioId = usuarioId;
        midia.tipo = tipo;
        midia.anuncioId = anuncioId;
        midia.urlS3 = urlS3;
        midia.mime = mime;
        midia.largura = largura;
        midia.altura = altura;
        midia.bytes = bytes;
        // Toda foto é regravada a partir dos pixels (ImagemHelper): nenhum metadado do original sobrevive.
        midia.exifRemovido = true;
        return midia;
    }

    /** Tira a mídia de uso; o objeto no storage é apagado depois do commit. */
    public void expurgar(Instant agora) {
        if (expurgoEm == null)
            expurgoEm = agora;
    }
}
