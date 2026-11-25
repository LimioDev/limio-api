package com.limio.api.modulos.moderacao.denuncia;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.moderacao.enums.TipoSancao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Sanção aplicada ao denunciado por uma decisão procedente (ETI-116). O motivo é o fundamento da decisão: o painel v1
 * não tem campo de motivo separado. {@code inicioEm}/{@code fimEm} só na suspensão; {@code avaliacaoId} só ao
 * ocultar avaliação. {@code usuarioId} é cru (ADR-0001 §1.6).
 */
@Entity
@Table(name = "sancao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sancao extends BaseEntity {

    @Column(name = "decisao_id", nullable = false, updatable = false)
    private UUID decisaoId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 30)
    private TipoSancao tipo;

    @Column(name = "motivo", nullable = false, updatable = false, length = DecisaoModeracao.FUNDAMENTO_MAX)
    private String motivo;

    @Column(name = "inicio_em", updatable = false)
    private Instant inicioEm;

    @Column(name = "fim_em", updatable = false)
    private Instant fimEm;

    @Column(name = "avaliacao_id", updatable = false)
    private UUID avaliacaoId;

    @Column(name = "aplicada_em", nullable = false, updatable = false)
    private Instant aplicadaEm;

    /**
     * Quem chama já validou o pedido: período só na suspensão, avaliação só ao ocultar (as constraints da V43 conferem
     * de novo). Não persiste.
     */
    public static Sancao aplicar(DecisaoModeracao decisao, UUID usuarioId, TipoSancao tipo, Instant inicioEm,
            Instant fimEm, UUID avaliacaoId) {
        Sancao sancao = new Sancao();
        sancao.decisaoId = decisao.getId();
        sancao.usuarioId = usuarioId;
        sancao.tipo = tipo;
        sancao.motivo = decisao.getFundamento();
        sancao.inicioEm = inicioEm;
        sancao.fimEm = fimEm;
        sancao.avaliacaoId = avaliacaoId;
        sancao.aplicadaEm = decisao.getDecididaEm();
        return sancao;
    }
}
