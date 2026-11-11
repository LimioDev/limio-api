package com.limio.api.modulos.financeiro.story;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.sessao.SessaoRepository;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldRepository;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.CobrancaRepository;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal.DocumentoFiscalRepository;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamentoRepository;
import com.limio.api.modulos.financeiro.records.AssinarGoldRequest;
import com.limio.api.modulos.financeiro.records.TokenizarCartaoRequest;
import com.limio.api.modulos.financeiro.records.TokenizarCartaoResponse;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD contra um Asaas falso (servidor HTTP do JDK, sem rede): prova que a fiação inteira
 * ({@code financeiro.gateway.provedor=asaas} — {@code GatewayPagamentoAsaas}, {@code AsaasClient},
 * {@code CartaoController}, {@code AsaasWebhookController}) sobe e funciona ponta a ponta ANTES de
 * testar contra o sandbox real do Asaas. Contexto Spring separado de {@code GoldStoryTest} (propriedades
 * diferentes): {@code GatewayPagamentoSimulado} nem existe aqui.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class GoldAsaasStoryTest {

    private static final String SENHA = "senhaForte123";
    private static HttpServer asaasFalso;

    @DynamicPropertySource
    static void propriedadesDoAsaasFalso(DynamicPropertyRegistry registry) throws IOException {
        asaasFalso = iniciarAsaasFalso();
        int porta = asaasFalso.getAddress().getPort();
        registry.add("financeiro.gateway.provedor", () -> "asaas");
        registry.add("asaas.base-url", () -> "http://127.0.0.1:" + porta);
        registry.add("asaas.api-key", () -> "test-key");
        registry.add("asaas.webhook.token", () -> "test-webhook-token");
    }

    private static HttpServer iniciarAsaasFalso() throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/customers", troca -> responder(troca, 200, "{\"id\":\"cus_fake001\"}"));
        servidor.createContext("/creditCard/tokenize", troca -> responder(troca, 200,
                "{\"creditCardToken\":\"tok_fake_abc\",\"creditCardBrand\":\"VISA\",\"creditCardNumber\":\"****4321\"}"));
        servidor.createContext("/payments", troca -> {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (corpo.contains("tok_recusado")) {
                responder(troca, 400, "{\"errors\":[{\"code\":\"invalid_creditCard\"}]}");
            } else {
                responder(troca, 200,
                        "{\"id\":\"pay_fake001\",\"status\":\"CONFIRMED\",\"creditCard\":{\"creditCardBrand\":\"VISA\",\"creditCardNumber\":\"****4321\"}}");
            }
        });
        servidor.start();
        return servidor;
    }

    private static void responder(com.sun.net.httpserver.HttpExchange troca, int status, String corpo)
            throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        troca.sendResponseHeaders(status, bytes.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SessaoRepository sessaoRepository;

    @Autowired
    private AssinaturaGoldRepository assinaturaGoldRepository;

    @Autowired
    private CobrancaRepository cobrancaRepository;

    @Autowired
    private DocumentoFiscalRepository documentoFiscalRepository;

    @Autowired
    private MeioPagamentoRepository meioPagamentoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void limparBase() {
        documentoFiscalRepository.deleteAll();
        cobrancaRepository.deleteAll();
        assinaturaGoldRepository.deleteAll();
        meioPagamentoRepository.deleteAll();
        sessaoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private Usuario prestador(String nome, String email, String cpf) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nome)
                .email(email)
                .telefone("11999998888")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(25))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(PapelUsuario.PRESTADOR)
                .statusConta(StatusConta.ATIVA)
                .telefoneConfirmadoEm(Instant.now())
                .emailConfirmadoEm(Instant.now())
                .build());
    }

    private String logar(String email) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, SENHA, "celular"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    private ResultActions tokenizar(String token) throws Exception {
        var request = new TokenizarCartaoRequest("RAFAEL SOUZA", "5162306219378829", "05", "2030", "318",
                "Rafael Souza", "rafael@example.com", "11144477735", "01310000", "100", "11999998888");
        return mockMvc.perform(post("/financeiro/gold/cartao")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions assinar(String token, String gatewayToken) throws Exception {
        String corpo = objectMapper.writeValueAsString(new AssinarGoldRequest("GOLD", gatewayToken));
        return mockMvc.perform(post("/financeiro/gold/assinatura")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    @Test
    void dadoCartaoTokenizadoEAssinaturaAprovada_entao201NasDuasEtapas() throws Exception {
        prestador("Rafael Souza", "rafael@example.com", "11144477735");
        String token = logar("rafael@example.com");

        String corpoTokenizacao = tokenizar(token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gatewayToken", is("tok_fake_abc")))
                .andExpect(jsonPath("$.bandeira", is("VISA")))
                .andReturn().getResponse().getContentAsString();
        var tokenizado = objectMapper.readValue(corpoTokenizacao, TokenizarCartaoResponse.class);

        assinar(token, tokenizado.gatewayToken())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ATIVA")));

        org.assertj.core.api.Assertions.assertThat(assinaturaGoldRepository.findAll()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(cobrancaRepository.findAll()).hasSize(1);
    }

    @Test
    void dadoAsaasRecusandoOCartao_quandoAssina_entao402() throws Exception {
        prestador("Rafael Souza", "rafael@example.com", "11144477735");
        String token = logar("rafael@example.com");

        assinar(token, "tok_recusado")
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.codigo", is("PAGAMENTO_RECUSADO")));
    }

    @Test
    void dadoTokenDeWebhookCorreto_entao200() throws Exception {
        mockMvc.perform(post("/financeiro/gold/webhook/asaas")
                        .header("asaas-access-token", "test-webhook-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"PAYMENT_CONFIRMED\",\"payment\":{\"id\":\"pay_fake001\",\"status\":\"CONFIRMED\"}}"))
                .andExpect(status().isOk());
    }

    @Test
    void dadoTokenDeWebhookErrado_entao401() throws Exception {
        mockMvc.perform(post("/financeiro/gold/webhook/asaas")
                        .header("asaas-access-token", "token-errado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"PAYMENT_CONFIRMED\"}"))
                .andExpect(status().isUnauthorized());
    }
}
