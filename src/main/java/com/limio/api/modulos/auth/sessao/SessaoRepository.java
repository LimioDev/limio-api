package com.limio.api.modulos.auth.sessao;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;

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
}
