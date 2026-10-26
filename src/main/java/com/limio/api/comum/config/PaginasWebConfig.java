package com.limio.api.comum.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Páginas web abertas pelos links dos e-mails (TICKET-0047, ADR-0002). São HTML estático em
 * {@code src/main/resources/static/paginas/}, sem template engine: o JavaScript da página lê o token do
 * fragmento da URL e chama os mesmos endpoints JSON que o app usa. Nenhuma regra mora na página — validade
 * e uso único do link ficam nos usecases.
 *
 * Aqui só ficam as URLs limpas que vão nos e-mails ({@code /nova-senha}, {@code /confirmar-email}).
 */
@Configuration
public class PaginasWebConfig implements WebMvcConfigurer {

    /** Liberadas sem token no {@link SecurityConfig}: quem abre o link do e-mail não está logado. */
    static final String[] ROTAS_PUBLICAS = { "/nova-senha", "/confirmar-email", "/paginas/**" };

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/nova-senha").setViewName("forward:/paginas/nova-senha.html");
        registry.addViewController("/confirmar-email").setViewName("forward:/paginas/confirmar-email.html");
    }
}
