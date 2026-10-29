package com.limio.api.modulos.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface ChatRepository extends BaseRepository<Chat, UUID> {

    Optional<Chat> findByAplicacaoId(UUID aplicacaoId);

    boolean existsByAplicacaoId(UUID aplicacaoId);

    List<Chat> findByAnuncioIdAndSomenteLeituraFalse(UUID anuncioId);
}
