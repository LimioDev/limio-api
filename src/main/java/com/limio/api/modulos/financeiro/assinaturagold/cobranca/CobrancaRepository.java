package com.limio.api.modulos.financeiro.assinaturagold.cobranca;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums.StatusCobranca;

public interface CobrancaRepository extends BaseRepository<Cobranca, UUID> {

    /** Consulta escalar, sem carregar a cobrança: quem chama trava pela assinatura antes de ler. */
    @Query("SELECT c.id FROM Cobranca c WHERE c.chaveIdempotencia = :chave")
    Optional<UUID> findIdByChaveIdempotencia(@Param("chave") String chave);

    @Query("SELECT c.id FROM Cobranca c WHERE c.gatewayCobrancaId = :gatewayCobrancaId")
    Optional<UUID> findIdByGatewayCobrancaId(@Param("gatewayCobrancaId") String gatewayCobrancaId);

    @Query("SELECT c.assinaturaGoldId FROM Cobranca c WHERE c.id = :id")
    Optional<UUID> findAssinaturaGoldIdById(@Param("id") UUID id);

    Optional<Cobranca> findFirstByAssinaturaGoldIdAndStatusOrderByCriadoEmDesc(UUID assinaturaGoldId,
            StatusCobranca status);

    long countByAssinaturaGoldIdAndInicioCicloCobradoAndStatus(UUID assinaturaGoldId, Instant inicioCicloCobrado,
            StatusCobranca status);
}
