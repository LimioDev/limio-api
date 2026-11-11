package com.limio.api.modulos.financeiro.assinaturagold.cobranca;

import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums.StatusCobranca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Cobrança de uma {@link AssinaturaGold} no gateway (ETI-81), subentidade sem ciclo de vida próprio.
 * Só cobrança aprovada é persistida: uma recusa não grava nada, o Prestador permanece FREE.
 * {@code chaveIdempotencia} tem índice único no banco — rede de segurança contra cobrar duas vezes se a
 * trava transacional do usecase falhar sob corrida real (mesmo padrão de {@code uk_aplicacao_par_ativo}).
 */
@Entity
@Table(name = "cobranca")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cobranca extends BaseEntity {

    @Column(name = "assinatura_gold_id", nullable = false, updatable = false)
    private UUID assinaturaGoldId;

    @Column(name = "valor_centavos", nullable = false, updatable = false)
    private long valorCentavos;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10, updatable = false)
    private StatusCobranca status;

    @Column(name = "gateway_cobranca_id", updatable = false)
    private String gatewayCobrancaId;

    @Column(name = "chave_idempotencia", nullable = false, updatable = false, length = 100)
    private String chaveIdempotencia;

    private Cobranca(UUID assinaturaGoldId, long valorCentavos, String gatewayCobrancaId, String chaveIdempotencia) {
        this.assinaturaGoldId = assinaturaGoldId;
        this.valorCentavos = valorCentavos;
        this.status = StatusCobranca.APROVADA;
        this.gatewayCobrancaId = gatewayCobrancaId;
        this.chaveIdempotencia = chaveIdempotencia;
    }

    public static Cobranca paga(UUID assinaturaGoldId, long valorCentavos, String gatewayCobrancaId,
            String chaveIdempotencia) {
        return new Cobranca(assinaturaGoldId, valorCentavos, gatewayCobrancaId, chaveIdempotencia);
    }
}
