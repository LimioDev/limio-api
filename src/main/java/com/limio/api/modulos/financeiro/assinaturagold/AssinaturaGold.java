package com.limio.api.modulos.financeiro.assinaturagold;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.financeiro.assinaturagold.enums.StatusAssinatura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Assinatura do Prestador ao plano GOLD (ETI-81). {@code usuarioId} é coluna crua, sem FK pra {@code
 * Usuario} de {@code modulos.auth} (ADR-0001 §1.6). No máximo uma por usuário: reassinar depois de
 * cancelada ({@link StatusAssinatura#CANCELADA}, card futuro US26/US27) reativa esta mesma linha em vez
 * de criar outra, pra manter o GOLD sem interrupção quando o ciclo ainda está vigente.
 */
@Entity
@Table(name = "assinatura_gold")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssinaturaGold extends BaseEntity {

    @Column(name = "usuario_id", nullable = false, unique = true, updatable = false)
    private UUID usuarioId;

    @Column(name = "plano_id", nullable = false)
    private UUID planoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private StatusAssinatura status;

    @Column(name = "data_inicio_ciclo", nullable = false)
    private Instant dataInicioCiclo;

    @Column(name = "data_fim_ciclo", nullable = false)
    private Instant dataFimCiclo;

    private AssinaturaGold(UUID usuarioId, UUID planoId, Instant dataInicioCiclo, Instant dataFimCiclo) {
        this.usuarioId = usuarioId;
        this.planoId = planoId;
        this.status = StatusAssinatura.ATIVA;
        this.dataInicioCiclo = dataInicioCiclo;
        this.dataFimCiclo = dataFimCiclo;
    }

    public static AssinaturaGold nova(UUID usuarioId, UUID planoId, Instant dataInicioCiclo, Instant dataFimCiclo) {
        return new AssinaturaGold(usuarioId, planoId, dataInicioCiclo, dataFimCiclo);
    }

    /** Reativa uma assinatura cancelada no mesmo ciclo em vez de criar outra linha para o usuário. */
    public void reativar(UUID planoId, Instant dataInicioCiclo, Instant dataFimCiclo) {
        this.planoId = planoId;
        this.status = StatusAssinatura.ATIVA;
        this.dataInicioCiclo = dataInicioCiclo;
        this.dataFimCiclo = dataFimCiclo;
    }

    public boolean estaAtiva() {
        return status == StatusAssinatura.ATIVA;
    }
}
