package com.limio.api.modulos.auth.actions.helper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Monta os links {@code https} que os e-mails levam às páginas web servidas pela própria API
 * (TICKET-0047, ADR-0002): {@code <app.web.url>/<pagina>#token=<token>}.
 *
 * O token vai no fragmento ({@code #}), não na query string: o navegador nunca envia o fragmento ao
 * servidor, então ele não aparece em log de acesso, de proxy nem no cabeçalho Referer. A página lê o
 * fragmento por JavaScript e manda o token no corpo do POST.
 */
@Component
public class LinkWeb {

    private final String base;

    public LinkWeb(@Value("${app.web.url}") String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public String pagina(String pagina, String token) {
        return base + "/" + pagina + "#token=" + token;
    }
}
