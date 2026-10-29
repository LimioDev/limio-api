package com.limio.api.modulos.chat.mensagem;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link Mensagem} — CRUD genérico (herdado) + finders da entity. */
@Component
public class MensagemService extends BaseService<Mensagem, UUID, MensagemRepository> {

    public MensagemService(MensagemRepository repository) {
        super(repository);
    }

    /** Uma página do histórico, da mais nova para a mais antiga. */
    public Slice<Mensagem> buscarHistorico(UUID chatId, int pagina, int tamanho) {
        return repository.findByChatIdOrderByDataEnvioDescIdDesc(chatId, PageRequest.of(pagina, tamanho));
    }

    /** Mensagens que a outra parte mandou para {@code usuarioId} e ainda não chegaram ao aparelho dele. */
    public List<Mensagem> buscarNaoEntreguesPara(UUID chatId, UUID usuarioId) {
        return repository.findByChatIdAndRemetenteIdNotAndEntregueEmIsNull(chatId, usuarioId);
    }

    /** Mensagens que a outra parte mandou para {@code usuarioId} e ele ainda não leu. */
    public List<Mensagem> buscarNaoLidasPor(UUID chatId, UUID usuarioId) {
        return repository.findByChatIdAndRemetenteIdNotAndLidaEmIsNull(chatId, usuarioId);
    }

    public List<Mensagem> salvarTodas(List<Mensagem> mensagens) {
        return repository.saveAll(mensagens);
    }
}
