package com.limio.api.modulos.financeiro.meiopagamento;

import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Token do gateway salvo pro Prestador (ETI-81) — nunca número de cartão nem CVV, só o que o gateway
 * devolveu na tokenização. {@code usuarioId} é coluna crua, sem FK pra {@code Usuario} de {@code
 * modulos.auth} (ADR-0001 §1.6). No máximo um por usuário: assinar com um cartão novo substitui o token
 * salvo, nunca acumula.
 */
@Entity
@Table(name = "meio_pagamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeioPagamento extends BaseEntity {

    @Column(name = "usuario_id", nullable = false, unique = true, updatable = false)
    private UUID usuarioId;

    @Column(name = "gateway_token", nullable = false)
    private String gatewayToken;

    @Column(name = "bandeira", nullable = false, length = 20)
    private String bandeira;

    @Column(name = "ultimos4", nullable = false, length = 4)
    private String ultimos4;

    private MeioPagamento(UUID usuarioId, String gatewayToken, String bandeira, String ultimos4) {
        this.usuarioId = usuarioId;
        this.gatewayToken = gatewayToken;
        this.bandeira = bandeira;
        this.ultimos4 = ultimos4;
    }

    public static MeioPagamento novo(UUID usuarioId, String gatewayToken, String bandeira, String ultimos4) {
        return new MeioPagamento(usuarioId, gatewayToken, bandeira, ultimos4);
    }

    /** Substitui o token e os dados de exibição pelo cartão usado na assinatura mais recente. */
    public void substituirToken(String gatewayToken, String bandeira, String ultimos4) {
        this.gatewayToken = gatewayToken;
        this.bandeira = bandeira;
        this.ultimos4 = ultimos4;
    }
}
