package com.limio.api.modulos.auth.sessao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

import jakarta.persistence.LockModeType;

public interface SessaoRepository extends BaseRepository<Sessao, UUID> {

    /**
     * {@code SELECT ... FOR UPDATE}: duas renovações simultâneas com o mesmo
     * refresh token não passam juntas — a segunda espera a primeira trocar o
     * hash e aí não encontra mais a linha.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Sessao> findByRefreshTokenHash(String refreshTokenHash);

    Optional<Sessao> findByRefreshTokenHashAndUsuarioId(String refreshTokenHash, UUID usuarioId);

    List<Sessao> findByUsuarioIdAndRevogadaEmIsNull(UUID usuarioId);

    /** Marco Civil da Internet art. 15: registro de acesso guardado por 6 meses. */
    @Modifying
    @Query("delete from Sessao s where (s.revogadaEm is not null and s.revogadaEm < :limite) or s.expiraEm < :limite")
    void apagarEncerradasOuExpiradasAntesDe(@Param("limite") Instant limite);
}
