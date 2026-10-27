package com.limio.api.modulos.perfil.endereco;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

/**
 * Todo finder já filtra {@code removidoEm is null}: endereço removido não aparece em
 * leitura nenhuma (soft delete, sem filtro global do Hibernate).
 */
public interface EnderecoSalvoRepository extends BaseRepository<EnderecoSalvo, UUID> {

    List<EnderecoSalvo> findAllByPerfilEmpregadorIdAndRemovidoEmIsNullOrderByPadraoDescApelidoAsc(
            UUID perfilEmpregadorId);

    Optional<EnderecoSalvo> findByIdAndPerfilEmpregadorIdAndRemovidoEmIsNull(UUID id, UUID perfilEmpregadorId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update EnderecoSalvo e
               set e.padrao = false, e.atualizadoEm = :agora
             where e.perfilEmpregadorId = :perfilEmpregadorId
               and e.padrao = true
               and e.removidoEm is null
            """)
    int desmarcarPadrao(@Param("perfilEmpregadorId") UUID perfilEmpregadorId, @Param("agora") Instant agora);
}
