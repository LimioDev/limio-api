package com.limio.api.modulos.anuncio.reagendamento;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Proposta de nova data pra um {@code Anuncio}. {@code anuncioId} é coluna crua (ADR-0001 §1.6). */
@Entity
@Table(name = "reagendamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reagendamento extends BaseEntity {

    @Column(name = "anuncio_id", nullable = false)
    private UUID anuncioId;

    @Column(name = "data_anterior", nullable = false)
    private Instant dataAnterior;

    @Column(name = "data_proposta", nullable = false)
    private Instant dataProposta;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusReagendamento status;

    @Column(name = "justificativa")
    private String justificativa;

    @Column(name = "respondido_em")
    private Instant respondidoEm;

    public void cancelarSemEfeito(Instant agora) {
        this.status = StatusReagendamento.CANCELADO;
        this.respondidoEm = agora;
    }

    /**
     * RB36: o reagendamento foi respondido há menos que a {@code carencia}. Sem resposta registrada não há carência.
     * Quem chama já filtrou o {@code ACEITO}; aqui só se conta o tempo.
     */
    public boolean estaNaCarencia(Duration carencia, Instant agora) {
        return respondidoEm != null && respondidoEm.plus(carencia).isAfter(agora);
    }
}
