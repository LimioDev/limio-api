package com.limio.api.modulos.perfil.empregador;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

public interface PerfilEmpregadorRepository extends BaseRepository<PerfilEmpregador, UUID> {

    Optional<PerfilEmpregador> findByUsuarioId(UUID usuarioId);

    @Modifying
    @Query(value = """
            insert into perfil_empregador (usuario_id, nome_exibicao)
            values (:usuarioId, :nomeExibicao)
            on conflict (usuario_id) do nothing
            """, nativeQuery = true)
    int inserirSeAusente(@Param("usuarioId") UUID usuarioId, @Param("nomeExibicao") String nomeExibicao);
}
