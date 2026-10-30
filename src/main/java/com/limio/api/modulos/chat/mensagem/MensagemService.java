package com.limio.api.modulos.chat.mensagem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link Mensagem} — CRUD genérico (herdado) + finders da entity. */
@Component
public class MensagemService extends BaseService<Mensagem, UUID, MensagemRepository> {

    public MensagemService(MensagemRepository repository) {
        super(repository);
    }

    /**
     * Uma página do histórico, da mais nova para a mais antiga: as mais recentes sem {@code antesDe}, ou as anteriores
     * à mensagem {@code antesDe}.
     */
    public Slice<Mensagem> buscarHistorico(UUID chatId, UUID antesDe, int tamanho) {
        Pageable pagina = PageRequest.ofSize(tamanho);
        return antesDe == null
                ? repository.findByChatIdOrderByDataEnvioDescIdDesc(chatId, pagina)
                : repository.buscarAntesDe(chatId, antesDe, pagina);
    }

    /** Ids das mensagens recebidas por {@code usuarioId} que só agora chegaram ao aparelho dele. */
    public List<UUID> marcarEntregues(UUID chatId, UUID usuarioId, Instant agora) {
        return repository.marcarEntregues(chatId, usuarioId, agora);
    }

    /** Ids das mensagens recebidas por {@code usuarioId} que só agora ele leu. */
    public List<UUID> marcarLidas(UUID chatId, UUID usuarioId, Instant agora) {
        return repository.marcarLidas(chatId, usuarioId, agora);
    }
}
