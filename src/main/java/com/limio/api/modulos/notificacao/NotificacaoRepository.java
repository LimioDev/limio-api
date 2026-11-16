package com.limio.api.modulos.notificacao;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

public interface NotificacaoRepository extends BaseRepository<Notificacao, UUID> {

    /** Usa {@code idx_notificacao_usuario_enviada}. A ordem vem do nome; o {@code pageable} não traz {@code Sort}. */
    Page<Notificacao> findByUsuarioIdOrderByEnviadaEmDescIdDesc(UUID usuarioId, Pageable pageable);

    /** Usa {@code idx_notificacao_usuario_nao_lida}. */
    long countByUsuarioIdAndLidaEmIsNull(UUID usuarioId);

    Optional<Notificacao> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    /** Em lote, sem carregar as linhas. {@code atualizadoEm} vai junto: update em lote não passa pelo Hibernate. */
    @Modifying
    @Query("update Notificacao n set n.lidaEm = :agora, n.atualizadoEm = :agora "
            + "where n.usuarioId = :usuarioId and n.lidaEm is null")
    int marcarTodasComoLidas(@Param("usuarioId") UUID usuarioId, @Param("agora") Instant agora);

    @Modifying
    @Query("delete from Notificacao n where n.usuarioId = :usuarioId")
    int apagarDoUsuario(@Param("usuarioId") UUID usuarioId);
}
