package com.limio.api.modulos.auth.actions.helper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Monta os links que os e-mails levam ao app. Concentra a regra da barra entre a
 * base e a tela: base {@code https://app.limio} precisa de {@code /}, mas base de
 * deep link {@code limio://} já termina em barra, e uma terceira
 * ({@code limio:///tela}) transformaria a tela em path em vez de host.
 */
@Component
public class LinkApp {

    private final String base;

    public LinkApp(@Value("${app.frontend.url}") String base) {
        this.base = base;
    }

    public String linkApp(String tela) {
        return base.endsWith("/") ? base + tela : base + "/" + tela;
    }
}
