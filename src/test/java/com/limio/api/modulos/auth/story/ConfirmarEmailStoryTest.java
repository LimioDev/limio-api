package com.limio.api.modulos.auth.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.excecao.enums.CodigoErro;
import com.limio.api.modulos.auth.actions.service.EmailService;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta do TICKET-0047: o cadastro envia o link de confirmação, a página web
 * {@code /confirmar-email} chama {@code POST /auth/confirmar-email} (UC05), o app pede outro link por
 * {@code POST /auth/reenviar-confirmacao-email}, e as páginas são públicas. Sobe Spring context +
 * Testcontainers Postgres; o envio de e-mail é espiado (só loga com {@code email.enviar=false}).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConfirmarEmailStoryTest {

    private static final String EMAIL = "maria@example.com";
    private static final String SENHA = "senhaForte123";
    private static final String PREFIXO_LINK = "http://localhost:8080/confirmar-email#token=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private EmailService emailService;

    @BeforeEach
    @AfterEach
    void limparBase() {
        jdbc.execute("delete from cpf_bloqueado");
        jdbc.execute("TRUNCATE TABLE usuario CASCADE");
        clearInvocations(emailService);
    }

    // ---------- apoio ----------

    private void cadastrar() throws Exception {
        mockMvc.perform(post("/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "nomeCompleto", "Maria da Silva", "email", EMAIL, "telefone", "11999998888",
                        "senha", SENHA, "cpf", "52998224725", "dataNascimento", "1990-01-01",
                        "cidadeUf", "São Paulo/SP"))))
                .andExpect(status().isCreated());
    }

    private List<String> linksEnviados(int vezes) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(vezes)).enviarConfirmacaoEmail(eq(EMAIL), link.capture());
        return link.getAllValues();
    }

    private static String tokenDoLink(String link) {
        assertThat(link).startsWith(PREFIXO_LINK);
        return link.substring(PREFIXO_LINK.length());
    }

    private ResultActions confirmar(String token) throws Exception {
        return mockMvc.perform(post("/auth/confirmar-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token))));
    }

    private ResultActions reenviar(String email) throws Exception {
        return mockMvc.perform(post("/auth/reenviar-confirmacao-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private Timestamp emailConfirmadoEm() {
        return jdbc.queryForObject("select email_confirmado_em from usuario where email = ?", Timestamp.class, EMAIL);
    }

    private List<Integer> emParalelo(List<Callable<Integer>> tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (Callable<Integer> tarefa : tarefas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();
            List<Integer> status = new ArrayList<>();
            for (Future<Integer> futuro : futuros) {
                status.add(futuro.get());
            }
            return status;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- cadastro + confirmação ----------

    @Test
    void dadoCadastro_entaoEnviaUmLinkHttpsDaPaginaWebComOTokenNoFragmento() throws Exception {
        cadastrar();

        String link = linksEnviados(1).get(0);
        assertThat(tokenDoLink(link)).isNotBlank();
        assertThat(emailConfirmadoEm()).isNull();
    }

    @Test
    void dadoLinkDoCadastro_quandoConfirma_entaoEmailConfirmadoEOLinkNaoValeDeNovo() throws Exception {
        cadastrar();
        String token = tokenDoLink(linksEnviados(1).get(0));

        confirmar(token).andExpect(status().isOk());
        assertThat(emailConfirmadoEm()).isNotNull();

        confirmar(token).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is(CodigoErro.LINK_CONFIRMACAO_EMAIL_INVALIDO.name())));
    }

    @Test
    void dadoLinkExpirado_quandoConfirma_entaoRecusaSemConfirmar() throws Exception {
        cadastrar();
        String token = tokenDoLink(linksEnviados(1).get(0));
        jdbc.update("update verificacao_contato set expira_em = now() - interval '1 minute'");

        confirmar(token).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is(CodigoErro.LINK_CONFIRMACAO_EMAIL_INVALIDO.name())));
        assertThat(emailConfirmadoEm()).isNull();
    }

    @Test
    void dadoTokenInventado_quandoConfirma_entao401() throws Exception {
        confirmar("token-que-nao-existe").andExpect(status().isUnauthorized());
    }

    @Test
    void dadoTokenEmBranco_quandoConfirma_entao400() throws Exception {
        confirmar(" ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is(CodigoErro.VALIDACAO_FALHOU.name())));
    }

    @Test
    void dadoLinkDeConfirmacao_quandoUsadoParaRedefinirSenha_entaoRecusa() throws Exception {
        cadastrar();
        String token = tokenDoLink(linksEnviados(1).get(0));

        mockMvc.perform(post("/auth/redefinir-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token, "novaSenha", "outraSenha99"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is(CodigoErro.TOKEN_RECUPERACAO_INVALIDO.name())));
    }

    @Test
    void dadoDoisCliquesSimultaneosNoMesmoLink_entaoSoUmConfirma() throws Exception {
        cadastrar();
        String token = tokenDoLink(linksEnviados(1).get(0));

        List<Integer> status = emParalelo(List.of(
                () -> confirmar(token).andReturn().getResponse().getStatus(),
                () -> confirmar(token).andReturn().getResponse().getStatus()));

        assertThat(status).containsExactlyInAnyOrder(200, 401);
    }

    /** D10: o link de confirmação não vale se o e-mail da conta já for outro. */
    @Test
    void dadoEmailDaContaAlteradoPorFora_quandoConfirmaOLinkAntigo_entaoRecusa() throws Exception {
        cadastrar();
        String token = tokenDoLink(linksEnviados(1).get(0));
        jdbc.update("update usuario set email = 'outra@example.com' where email = ?", EMAIL);

        confirmar(token).andExpect(status().isUnauthorized());
    }

    @Test
    void dadoContaEncerrada_entaoOsLinksPendentesSaoApagados() throws Exception {
        cadastrar();
        String corpo = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "senha", SENHA))))
                .andReturn().getResponse().getContentAsString();
        String acesso = objectMapper.readTree(corpo).get("token").asString();

        mockMvc.perform(post("/auth/encerrar-conta")
                .header("Authorization", "Bearer " + acesso)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("confirmacao", true))))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from verificacao_contato", Integer.class)).isZero();
    }

    // ---------- reenviar link ----------

    @Test
    void dadoEmailNaoConfirmado_quandoReenvia_entaoNovoLinkEOAnteriorDeixaDeValer() throws Exception {
        cadastrar();

        reenviar(EMAIL).andExpect(status().isOk());

        List<String> links = linksEnviados(2);
        confirmar(tokenDoLink(links.get(0))).andExpect(status().isUnauthorized());
        confirmar(tokenDoLink(links.get(1))).andExpect(status().isOk());
    }

    @Test
    void dadoEmailSemConta_quandoReenvia_entaoMesmaRespostaSemEnviarNada() throws Exception {
        reenviar("ninguem@example.com").andExpect(status().isOk());

        verify(emailService, never()).enviarConfirmacaoEmail(anyString(), anyString());
    }

    @Test
    void dadoEmailJaConfirmado_quandoReenvia_entaoMesmaRespostaSemEnviarNada() throws Exception {
        cadastrar();
        confirmar(tokenDoLink(linksEnviados(1).get(0))).andExpect(status().isOk());

        reenviar(EMAIL).andExpect(status().isOk());

        linksEnviados(1);
    }

    /** O link do cadastro conta no limite de 3 e-mails de confirmação por hora. */
    @Test
    void dadoCadastroEDoisReenvios_quandoReenviaDeNovo_entaoMesmaRespostaSemEnviar() throws Exception {
        cadastrar();
        reenviar(EMAIL).andExpect(status().isOk());
        reenviar(EMAIL).andExpect(status().isOk());

        reenviar(EMAIL).andExpect(status().isOk());

        linksEnviados(3);
        Integer emitidos = jdbc.queryForObject("select count(*) from verificacao_contato", Integer.class);
        assertThat(emitidos).isEqualTo(3);
    }

    @Test
    void dadoEmailInvalido_quandoReenvia_entao400() throws Exception {
        reenviar("nao-e-email").andExpect(status().isBadRequest());
    }

    // ---------- páginas web ----------

    @Test
    void paginasSaoPublicasEServidasPelaApi() throws Exception {
        mockMvc.perform(get("/confirmar-email"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/paginas/confirmar-email.html"));
        mockMvc.perform(get("/nova-senha"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/paginas/nova-senha.html"));
    }

    @Test
    void arquivosDasPaginasSaoPublicosEComCabecalhosDeProtecao() throws Exception {
        mockMvc.perform(get("/paginas/nova-senha.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Crie uma nova senha")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        mockMvc.perform(get("/paginas/confirmar-email.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reenviar link")));
        mockMvc.perform(get("/paginas/nova-senha.js")).andExpect(status().isOk());
        mockMvc.perform(get("/paginas/confirmar-email.js")).andExpect(status().isOk());
        mockMvc.perform(get("/paginas/comum.js")).andExpect(status().isOk());
        mockMvc.perform(get("/paginas/limio.css")).andExpect(status().isOk());
    }

    @Test
    void liberarAsPaginasNaoAbreOResto() throws Exception {
        mockMvc.perform(get("/conta/preferencias-notificacao")).andExpect(status().isUnauthorized());
    }
}
