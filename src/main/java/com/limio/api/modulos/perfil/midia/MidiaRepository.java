package com.limio.api.modulos.perfil.midia;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.perfil.midia.enums.TipoMidia;

public interface MidiaRepository extends BaseRepository<Midia, UUID> {

    Optional<Midia> findByUsuarioIdAndTipoAndExpurgoEmIsNull(UUID usuarioId, TipoMidia tipo);

    /**
     * Lock do Postgres por usuário, solto no fim da transação de quem chama. Forma de duas chaves
     * (namespace {@code 'midia_foto_perfil'} + usuário): não colide com os locks de outros fluxos.
     */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtext('midia_foto_perfil'), hashtext(:usuarioId))",
            nativeQuery = true)
    Integer travarFotoPerfilPorUsuario(@Param("usuarioId") String usuarioId);
}
