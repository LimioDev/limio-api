package com.limio.api.modulos.notificacao.aparelhopush;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

public interface AparelhoPushRepository extends BaseRepository<AparelhoPush, UUID> {

    Optional<AparelhoPush> findByToken(String token);

    /**
     * Upsert atômico por {@code token}: dois registros simultâneos do mesmo aparelho não violam {@code
     * uk_aparelho_push_token}, e o aparelho onde outra conta entrou passa pra ela.
     */
    @Modifying
    @Query(value = """
            insert into aparelho_push (usuario_id, token, plataforma)
            values (:usuarioId, :token, :plataforma)
            on conflict (token) do update
               set usuario_id = excluded.usuario_id, plataforma = excluded.plataforma, atualizado_em = now()
            """, nativeQuery = true)
    void registrar(@Param("usuarioId") UUID usuarioId, @Param("token") String token,
            @Param("plataforma") String plataforma);

    /** Só o do próprio usuário: token de outra conta fica como está. */
    @Modifying
    @Query("delete from AparelhoPush a where a.token = :token and a.usuarioId = :usuarioId")
    int apagarTokenDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("token") String token);

    List<AparelhoPush> findByUsuarioId(UUID usuarioId);

    @Modifying
    @Query("delete from AparelhoPush a where a.token in :tokens")
    int apagarTokens(@Param("tokens") Collection<String> tokens);

    @Modifying
    @Query("delete from AparelhoPush a where a.usuarioId = :usuarioId")
    int apagarDoUsuario(@Param("usuarioId") UUID usuarioId);
}
