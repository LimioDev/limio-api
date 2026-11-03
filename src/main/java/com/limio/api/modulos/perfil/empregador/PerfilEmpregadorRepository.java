package com.limio.api.modulos.perfil.empregador;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

import jakarta.persistence.LockModeType;

public interface PerfilEmpregadorRepository extends BaseRepository<PerfilEmpregador, UUID> {

    Optional<PerfilEmpregador> findByUsuarioId(UUID usuarioId);

    List<PerfilEmpregador> findByUsuarioIdIn(Collection<UUID> usuarioIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PerfilEmpregador p where p.id = :id")
    Optional<PerfilEmpregador> findByIdParaAtualizar(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            insert into perfil_empregador (usuario_id, nome_exibicao)
            values (:usuarioId, :nomeExibicao)
            on conflict (usuario_id) do nothing
            """, nativeQuery = true)
    int inserirSeAusente(@Param("usuarioId") UUID usuarioId, @Param("nomeExibicao") String nomeExibicao);
}
