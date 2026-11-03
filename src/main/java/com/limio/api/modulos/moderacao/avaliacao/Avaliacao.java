package com.limio.api.modulos.moderacao.avaliacao;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.comum.seguranca.enums.PapelUsuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Nota (e comentário opcional) que uma das partes deu à outra num serviço concluído (ETI-107). {@code anuncioId},
 * {@code avaliadorId} e {@code avaliadoId} são UUID crus, nunca relação JPA para entidades de outros módulos
 * (ADR-0001 §1.6). Imutável depois de publicada: não há edição de avaliação.
 */
@Entity
@Table(name = "avaliacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Avaliacao extends BaseEntity {

    public static final int NOTA_MINIMA = 1;
    public static final int NOTA_MAXIMA = 5;
    public static final int COMENTARIO_MAX = 400;

    @Column(name = "anuncio_id", nullable = false, updatable = false)
    private UUID anuncioId;

    @Column(name = "avaliador_id", nullable = false, updatable = false)
    private UUID avaliadorId;

    @Column(name = "avaliado_id", nullable = false, updatable = false)
    private UUID avaliadoId;

    /** Papel que o avaliado teve no serviço: decide em qual perfil a média entra. */
    @Enumerated(EnumType.STRING)
    @Column(name = "papel_avaliado", nullable = false, updatable = false, length = 20)
    private PapelUsuario papelAvaliado;

    @Column(name = "nota", nullable = false, updatable = false)
    private int nota;

    @Column(name = "comentario", updatable = false, length = COMENTARIO_MAX)
    private String comentario;

    @Column(name = "publicada_em", nullable = false, updatable = false)
    private Instant publicadaEm;

    private Avaliacao(UUID anuncioId, UUID avaliadorId, UUID avaliadoId, PapelUsuario papelAvaliado, int nota,
            String comentario, Instant publicadaEm) {
        this.anuncioId = anuncioId;
        this.avaliadorId = avaliadorId;
        this.avaliadoId = avaliadoId;
        this.papelAvaliado = papelAvaliado;
        this.nota = nota;
        this.comentario = comentario;
        this.publicadaEm = publicadaEm;
    }

    /** Quem chama já validou nota, comentário e janela; aqui só o retrato do que foi enviado. Não persiste. */
    public static Avaliacao publicar(UUID anuncioId, UUID avaliadorId, UUID avaliadoId, PapelUsuario papelAvaliado,
            int nota, String comentario, Instant agora) {
        return new Avaliacao(anuncioId, avaliadorId, avaliadoId, papelAvaliado, nota, comentario, agora);
    }
}
