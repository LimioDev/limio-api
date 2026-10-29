package com.limio.api.comum.records;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Lista paginada no contrato HTTP, sem expor o JSON interno do {@link Page} do Spring Data (que muda entre
 * versões). {@code pagina} começa em 0.
 */
public record PageResponse<T>(List<T> itens, int pagina, int tamanho, long totalItens, int totalPaginas) {

    /** Recebe a página já convertida pro record de resposta (ex.: {@code page.map(mapper::toResponse)}). */
    public static <T> PageResponse<T> de(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
