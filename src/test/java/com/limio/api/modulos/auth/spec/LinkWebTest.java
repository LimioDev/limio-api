package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.auth.actions.helper.LinkWeb;

class LinkWebTest {

    @Test
    void tokenVaiNoFragmentoParaNuncaChegarAoServidor() {
        assertThat(new LinkWeb("https://api.limio.test").pagina("confirmar-email", "abc"))
                .isEqualTo("https://api.limio.test/confirmar-email#token=abc");
    }

    @Test
    void baseTerminadaEmBarraNaoGeraBarraDupla() {
        assertThat(new LinkWeb("https://api.limio.test/").pagina("nova-senha", "abc"))
                .isEqualTo("https://api.limio.test/nova-senha#token=abc");
    }
}
