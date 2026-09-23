package com.limio.api.comum.base;

import java.io.Serializable;

import com.limio.api.comum.excecao.EntidadeNaoEncontradaException;

/**
 * Camada técnica genérica de acesso a dado, reaproveitada por todo módulo —
 * operações de CRUD comuns a qualquer entity, sem regra de negócio. Distinta
 * de {@code actions/service} (porta de integração externa: e-mail, storage,
 * gateway, fila) — esta classe fica em {@code comum/base} porque é infra
 * técnica, não um adapter de saída de um módulo específico.
 *
 * Regra de negócio (validação, decisão de fluxo) continua em
 * {@code actions/usecase} — o usecase depende do {@code <Entidade>Service}
 * do próprio módulo, que estende esta classe só pra ganhar as operações
 * genéricas e adicionar os finders específicos da entity.
 *
 * @param <E>  entity do módulo
 * @param <ID> tipo do identificador
 * @param <R>  repository do módulo, sempre um {@link BaseRepository}
 */
public abstract class BaseService<E extends BaseEntity, ID extends Serializable, R extends BaseRepository<E, ID>> {

    protected final R repository;

    protected BaseService(R repository) {
        this.repository = repository;
    }

    public E salvar(E entidade) {
        return repository.save(entidade);
    }

    public E buscarPorIdOuFalhar(ID id) {
        return repository.findById(id).orElseThrow(EntidadeNaoEncontradaException::new);
    }

    public boolean existePorId(ID id) {
        return repository.existsById(id);
    }

    public void remover(E entidade) {
        repository.delete(entidade);
    }
}
