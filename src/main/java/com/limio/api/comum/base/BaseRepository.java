package com.limio.api.comum.base;

import java.io.Serializable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Porta de persistência genérica reaproveitada por todo módulo. Repository de
 * módulo estende esta interface em vez de {@link JpaRepository} direto, pra
 * ganhar (e futuramente centralizar) comportamento comum a toda entity.
 *
 * {@code @NoRepositoryBean}: é um contrato intermediário, não um repository
 * concreto — Spring Data não deve tentar instanciá-la como bean sozinha.
 *
 * @param <E>  entity do módulo, sempre extends {@link BaseEntity}
 * @param <ID> tipo do identificador (UUID na quase totalidade dos módulos)
 */
@NoRepositoryBean
public interface BaseRepository<E extends BaseEntity, ID extends Serializable> extends JpaRepository<E, ID> {
}
