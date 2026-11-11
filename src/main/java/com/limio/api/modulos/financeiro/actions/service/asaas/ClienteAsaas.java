package com.limio.api.modulos.financeiro.actions.service.asaas;

import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Cache do customer do Asaas por usuário (ETI-81): evita criar um customer novo no Asaas a cada
 * cobrança. Só existe enquanto {@code GatewayPagamentoAsaas} for o provedor ativo — não é
 * {@code meio_pagamento} porque o id de customer é um conceito do Asaas, não genérico de gateway.
 */
@Entity
@Table(name = "cliente_asaas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClienteAsaas extends BaseEntity {

    @Column(name = "usuario_id", nullable = false, unique = true, updatable = false)
    private UUID usuarioId;

    @Column(name = "asaas_customer_id", nullable = false, updatable = false, length = 50)
    private String asaasCustomerId;

    private ClienteAsaas(UUID usuarioId, String asaasCustomerId) {
        this.usuarioId = usuarioId;
        this.asaasCustomerId = asaasCustomerId;
    }

    public static ClienteAsaas novo(UUID usuarioId, String asaasCustomerId) {
        return new ClienteAsaas(usuarioId, asaasCustomerId);
    }
}
