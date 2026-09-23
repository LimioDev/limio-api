package com.limio.api.comum.base;

/**
 * Anti-corruption layer entre entity e record. Implementado dentro de cada
 * módulo, chamado só pelo controller — nunca pelo usecase.
 *
 * @param <E>   entity JPA do módulo
 * @param <REQ> record de entrada (request HTTP)
 * @param <RES> record de saída (response HTTP)
 */
public interface BaseMapper<E, REQ, RES> {

    E toEntity(REQ request);

    RES toResponse(E entity);
}
