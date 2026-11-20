package com.limio.api.modulos.anuncio.reagendamento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;

public interface ReagendamentoRepository extends BaseRepository<Reagendamento, UUID> {

    List<Reagendamento> findByAnuncioIdInAndStatus(List<UUID> anuncioIds, StatusReagendamento status);

    @Query("select r.anuncioId from Reagendamento r where r.id = :id")
    Optional<UUID> findAnuncioIdById(@Param("id") UUID id);

    @Query(value = """
            SELECT r.id FROM reagendamento r
            WHERE r.status = 'PENDENTE'
              AND r.expira_em <= :agora
            ORDER BY r.expira_em, r.id
            LIMIT :lote
            """, nativeQuery = true)
    List<UUID> buscarIdsParaExpirar(@Param("agora") Instant agora, @Param("lote") int lote);

    boolean existsByAnuncioIdAndStatus(UUID anuncioId, StatusReagendamento status);

    boolean existsByAnuncioIdAndPropostaAposDataVigenteTrue(UUID anuncioId);

    long countByAnuncioIdAndStatus(UUID anuncioId, StatusReagendamento status);

    Optional<Reagendamento> findFirstByAnuncioIdAndStatusOrderByRespondidoEmDesc(UUID anuncioId,
            StatusReagendamento status);
}
