package com.limio.api.modulos.moderacao.denuncia;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.moderacao.enums.VereditoModeracao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Decisão do administrador sobre uma denúncia (ETI-116). Uma por denúncia, imutável. {@code administradorId} é o id
 * cru do {@code administrador} ({@code modulos.administracao}), sem relação JPA (ADR-0001 §1.6).
 */
@Entity
@Table(name = "decisao_moderacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DecisaoModeracao extends BaseEntity {

    public static final int FUNDAMENTO_MAX = 2000;

    @Column(name = "denuncia_id", nullable = false, updatable = false)
    private UUID denunciaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "veredito", nullable = false, updatable = false, length = 20)
    private VereditoModeracao veredito;

    @Column(name = "fundamento", nullable = false, updatable = false, length = FUNDAMENTO_MAX)
    private String fundamento;

    @Column(name = "administrador_id", nullable = false, updatable = false)
    private UUID administradorId;

    @Column(name = "decidida_em", nullable = false, updatable = false)
    private Instant decididaEm;

    /** Quem chama já validou o pedido. Não persiste. */
    public static DecisaoModeracao registrar(UUID denunciaId, VereditoModeracao veredito, String fundamento,
            UUID administradorId, Instant agora) {
        DecisaoModeracao decisao = new DecisaoModeracao();
        decisao.denunciaId = denunciaId;
        decisao.veredito = veredito;
        decisao.fundamento = fundamento;
        decisao.administradorId = administradorId;
        decisao.decididaEm = agora;
        return decisao;
    }
}
