package com.limio.api.modulos.notificacao.envionotificacao;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link EnvioNotificacao} — CRUD genérico (herdado) + finder da entity. */
@Component
public class EnvioNotificacaoService extends BaseService<EnvioNotificacao, UUID, EnvioNotificacaoRepository> {

    public EnvioNotificacaoService(EnvioNotificacaoRepository repository) {
        super(repository);
    }

    public List<EnvioNotificacao> buscarDaNotificacao(UUID notificacaoId) {
        return repository.findByNotificacaoId(notificacaoId);
    }

    /**
     * Transação própria: a entrega roda depois do commit de quem gravou a notificação, e nesse ponto uma escrita
     * {@code REQUIRED} entraria na transação já encerrada e não seria gravada.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void salvarTodos(List<EnvioNotificacao> envios) {
        repository.saveAll(envios);
    }
}
