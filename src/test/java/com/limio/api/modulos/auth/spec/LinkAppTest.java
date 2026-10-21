package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.auth.actions.helper.LinkApp;

class LinkAppTest {

    @Test
    void baseSemBarraFinalGanhaUmaBarra() {
        assertThat(new LinkApp("https://app.limio.test").linkApp("confirmar-email?token=abc"))
                .isEqualTo("https://app.limio.test/confirmar-email?token=abc");
    }

    @Test
    void baseDeDeepLinkJaTerminadaEmBarraNaoGanhaTerceira() {
        assertThat(new LinkApp("limio://").linkApp("confirmar-email?token=abc"))
                .isEqualTo("limio://confirmar-email?token=abc");
    }
}
