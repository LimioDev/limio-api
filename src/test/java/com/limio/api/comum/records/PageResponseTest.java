package com.limio.api.comum.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Contrato de paginação genérico (ETI-73). */
class PageResponseTest {

    @Test
    void dadoPaginaDoSpring_quandoConverte_entaoTrazConteudoConvertidoETotalDoFiltroInteiro() {
        PageImpl<Integer> page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 5);

        PageResponse<String> resposta = PageResponse.de(page, n -> "item-" + n);

        assertThat(resposta.content()).containsExactly("item-1", "item-2");
        assertThat(resposta.pagina()).isEqualTo(1);
        assertThat(resposta.tamanho()).isEqualTo(2);
        assertThat(resposta.totalElementos()).isEqualTo(5);
        assertThat(resposta.totalPaginas()).isEqualTo(3);
    }

    @Test
    void dadoPaginaVazia_quandoConverte_entaoListaVaziaETotalZero() {
        PageResponse<String> resposta = PageResponse.de(new PageImpl<Integer>(List.of(), PageRequest.of(0, 20), 0), String::valueOf);

        assertThat(resposta.content()).isEmpty();
        assertThat(resposta.totalElementos()).isZero();
        assertThat(resposta.totalPaginas()).isZero();
    }

    @Test
    void dadoListaMutavelOriginal_quandoCriaResposta_entaoConteudoNaoMudaDepois() {
        List<String> original = new ArrayList<>(List.of("a"));
        PageResponse<String> resposta = new PageResponse<>(original, 0, 20, 1, 1);

        original.add("b");

        assertThat(resposta.content()).containsExactly("a");
        assertThatThrownBy(() -> resposta.content().add("c")).isInstanceOf(UnsupportedOperationException.class);
    }
}
