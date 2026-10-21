package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.excecao.CategoriaProtegidaException;

class CategoriaTest {

    private static Categoria categoria(String nome, boolean protegida) {
        return Categoria.builder().nome(nome).grupo(nome).ordem(1).ativa(true).protegida(protegida).build();
    }

    @Test
    void deveDesativarCategoriaComum() {
        Categoria pintura = categoria("Pintura", false);

        pintura.desativar();

        assertThat(pintura.isAtiva()).isFalse();
    }

    @Test
    void deveRecusarDesativarCategoriaProtegidaMantendoAtiva() {
        Categoria outros = categoria("Outros", true);

        assertThatThrownBy(outros::desativar).isInstanceOf(CategoriaProtegidaException.class);
        assertThat(outros.isAtiva()).isTrue();
    }
}
