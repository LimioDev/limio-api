package com.limio.api.modulos.notificacao.preferencianotificacao;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

public interface PreferenciaNotificacaoRepository extends BaseRepository<PreferenciaNotificacao, UUID> {

    Optional<PreferenciaNotificacao> findByUsuarioId(UUID usuarioId);

    /**
     * Upsert atômico por {@code usuario_id}: dois PUTs concorrentes de quem ainda não tem registro
     * não violam {@code uk_preferencia_notificacao_usuario_id} — o perdedor cai no DO UPDATE.
     */
    @Modifying
    @Query(value = """
            insert into preferencia_notificacao (usuario_id, push, email, sms, whatsapp)
            values (:usuarioId, :push, :email, :sms, :whatsapp)
            on conflict (usuario_id) do update
               set push = excluded.push, email = excluded.email, sms = excluded.sms,
                   whatsapp = excluded.whatsapp, atualizado_em = now()
            """, nativeQuery = true)
    void upsert(@Param("usuarioId") UUID usuarioId, @Param("push") boolean push, @Param("email") boolean email,
            @Param("sms") boolean sms, @Param("whatsapp") boolean whatsapp);

    @Modifying
    @Query("delete from PreferenciaNotificacao p where p.usuarioId = :usuarioId")
    int apagarDoUsuario(@Param("usuarioId") UUID usuarioId);
}
