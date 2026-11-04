package com.limio.api.comum.records;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Página de resultados no contrato HTTP. {@code totalElementos} é o total do filtro inteiro (não só da página)
 */
public record PageResponse<T>(List<T> content, int pagina, int tamanho, long totalElementos, int totalPaginas) {

    public PageResponse {
        content = List.copyOf(content);
    }

    public static <E, T> PageResponse<T> de(Page<E> page, Function<E, T> conversor) {
        return new PageResponse<>(
                page.getContent().stream().map(conversor).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
