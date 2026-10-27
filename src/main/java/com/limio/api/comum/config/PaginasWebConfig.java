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

    /** Nome das páginas, usado tanto nas rotas abaixo quanto nos links que os usecases de {@code auth} montam. */
    public static final String NOVA_SENHA = "nova-senha";
    public static final String CONFIRMAR_EMAIL = "confirmar-email";

    /** Liberadas sem token no {@link SecurityConfig}: quem abre o link do e-mail não está logado. */
    static final String[] ROTAS_PUBLICAS = { "/" + NOVA_SENHA, "/" + CONFIRMAR_EMAIL, "/paginas/**" };

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/" + NOVA_SENHA).setViewName("forward:/paginas/" + NOVA_SENHA + ".html");
        registry.addViewController("/" + CONFIRMAR_EMAIL).setViewName("forward:/paginas/" + CONFIRMAR_EMAIL + ".html");
    }
}
