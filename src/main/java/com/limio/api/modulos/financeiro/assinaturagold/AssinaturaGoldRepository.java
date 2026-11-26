package com.limio.api.modulos.financeiro.assinaturagold;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

public interface AssinaturaGoldRepository extends BaseRepository<AssinaturaGold, UUID> {

    Optional<AssinaturaGold> findByUsuarioId(UUID usuarioId);

    /** Pré-seleção da rotina de renovação (ETI-85): mesma regra de {@link AssinaturaGold#renovacaoVencida}. */
    @Query(value = """
            SELECT id FROM assinatura_gold
            WHERE status = 'ATIVA'
              AND data_fim_ciclo <= :agora
              AND (proxima_tentativa_em IS NULL OR proxima_tentativa_em <= :agora)
            ORDER BY data_fim_ciclo
            LIMIT :lote
            """, nativeQuery = true)
    List<UUID> buscarIdsParaRenovar(@Param("agora") Instant agora, @Param("lote") int lote);

    /** Consulta escalar, sem carregar a entidade: quem chama trava pelo usuário antes de ler a assinatura. */
    @Query("SELECT a.usuarioId FROM AssinaturaGold a WHERE a.id = :id")
    Optional<UUID> findUsuarioIdById(@Param("id") UUID id);
}
