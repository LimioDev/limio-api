package com.limio.api.modulos.auth.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.sessao.SessaoRepository;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

/**
 * IP real atrás do proxy da Railway ({@code server.forward-headers-strategy=native}, decisão do tech lead
 * 2026-10-26). Precisa do Tomcat de verdade — o MockMvc não passa pelo RemoteIpValve. A conexão do teste
 * vem de 127.0.0.1, que conta como proxy interno, igual ao proxy da Railway em produção.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IpRealAtrasDoProxyStoryTest {

    private static final String EMAIL = "maria@example.com";
    private static final String SENHA = "senhaForte123";

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SessaoRepository sessaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void limparBase() {
        jdbc.execute("TRUNCATE TABLE usuario CASCADE");
        jdbc.execute("delete from tentativa_login");
        usuarioRepository.save(Usuario.builder()
                .nomeCompleto("Maria da Silva")
                .email(EMAIL)
                .telefone("11999998888")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf("52998224725")
                .dataNascimento(LocalDate.now().minusYears(25))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(StatusConta.ATIVA)
                .build());
    }

    private int login(String xForwardedFor, String senha) throws Exception {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", xForwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + EMAIL + "\",\"senha\":\"" + senha + "\",\"dispositivo\":\"celular\"}"))
                .build();
        return HttpClient.newHttpClient().send(requisicao, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void ipDoClienteVemDoProxyEOForjadoAEsquerdaEIgnorado() throws Exception {
        assertThat(login("1.2.3.4, 203.0.113.50", SENHA)).isEqualTo(200);

        assertThat(sessaoRepository.findAll()).singleElement()
                .satisfies(sessao -> assertThat(sessao.getIp()).isEqualTo("203.0.113.50"));
    }

    @Test
    void falhaDeLoginGuardaOIpDoCliente() throws Exception {
        assertThat(login("203.0.113.51", "senhaErrada99")).isEqualTo(401);

        assertThat(jdbc.queryForObject("select ip from tentativa_login", String.class)).isEqualTo("203.0.113.51");
    }
}
