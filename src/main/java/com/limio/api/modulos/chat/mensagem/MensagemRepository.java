package com.limio.api.modulos.chat.mensagem;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import com.limio.api.comum.base.BaseRepository;

public interface MensagemRepository extends BaseRepository<Mensagem, UUID> {

    Slice<Mensagem> findByChatIdOrderByDataEnvioDescIdDesc(UUID chatId, Pageable pagina);

    List<Mensagem> findByChatIdAndRemetenteIdNotAndEntregueEmIsNull(UUID chatId, UUID usuarioId);

    List<Mensagem> findByChatIdAndRemetenteIdNotAndLidaEmIsNull(UUID chatId, UUID usuarioId);
}
