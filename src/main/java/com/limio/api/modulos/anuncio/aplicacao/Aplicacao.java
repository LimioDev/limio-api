package com.limio.api.modulos.anuncio.aplicacao;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

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

/**
 * Candidatura de um prestador a um {@code Anuncio}. {@code anuncioId} e
 * {@code prestadorId} são colunas cruas — nunca relação JPA cruzando módulo
 * nem pro {@code Usuario} de {@code modulos.auth} (ADR-0001 §1.6).
 */
@Entity
@Table(name = "aplicacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Aplicacao extends BaseEntity {

    @Column(name = "anuncio_id", nullable = false)
    private UUID anuncioId;

    @Column(name = "prestador_id", nullable = false)
    private UUID prestadorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusAplicacao status;

    @Column(name = "data_aplicacao", nullable = false)
    private Instant dataAplicacao;

    @Column(name = "escolhida_em")
    private Instant escolhidaEm;

    @Column(name = "recusada_em")
    private Instant recusadaEm;

    @Column(name = "encerrada_em")
    private Instant encerradaEm;

    @Column(name = "motivo_encerramento")
    private String motivoEncerramento;

    public void encerrar(Instant agora, String motivo) {
        this.status = StatusAplicacao.ENCERRADA;
        this.encerradaEm = agora;
        this.motivoEncerramento = motivo;
    }
}
