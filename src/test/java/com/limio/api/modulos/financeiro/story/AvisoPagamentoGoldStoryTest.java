package com.limio.api.modulos.financeiro.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.CobrancaGoldAprovadaEvent;
import com.limio.api.comum.eventos.CobrancaGoldRecusadaEvent;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.financeiro.actions.usecase.RenovarGoldUseCase;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldRepository;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.Cobranca;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.CobrancaRepository;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal.DocumentoFiscalRepository;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums.StatusCobranca;
import com.limio.api.modulos.financeiro.assinaturagold.enums.StatusAssinatura;
import com.limio.api.modulos.financeiro.clienteasaas.ClienteAsaasRepository;
import com.limio.api.modulos.financeiro.eventos.GoldAssinadoEvent;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamento;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamentoRepository;
import com.limio.api.modulos.financeiro.plano.Plano;
import com.limio.api.modulos.financeiro.plano.PlanoRepository;
import com.sun.net.httpserver.HttpServer;

/**
 * BDD do aviso do gateway (webhook do Asaas) sobre as cobranças GOLD (ETI-85), contra um Asaas falso (servidor HTTP
 * do JDK, sem rede) e o banco real. Cobre o webhook válido (200), o repetido (sem registro duplicado), a assinatura
 * falsa (401 sem processar) e a renovação ponta a ponta pelo {@code GatewayPagamentoAsaas}, seguida do aviso que o
 * Asaas manda depois da cobrança já confirmada.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class AvisoPagamentoGoldStoryTest {

    private static final String TOKEN_WEBHOOK = "test-webhook-token";
    private static final String ROTA = "/financeiro/gold/webhook/asaas";
    private static HttpServer asaasFalso;

    @DynamicPropertySource
    static void propriedadesDoAsaasFalso(DynamicPropertyRegistry registry) throws IOException {
        asaasFalso = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        asaasFalso.createContext("/customers", troca -> responder(troca, 200, "{\"id\":\"cus_fake085\"}"));
        asaasFalso.createContext("/payments", troca -> {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (corpo.contains("tok_recusado")) {
                responder(troca, 400, "{\"errors\":[{\"code\":\"invalid_creditCard\"}]}");
            } else {
                responder(troca, 200, "{\"id\":\"pay_fake085\",\"status\":\"CONFIRMED\"}");
            }
        });
        asaasFalso.start();
        registry.add("financeiro.gateway.provedor", () -> "asaas");
        registry.add("asaas.base-url", () -> "http://127.0.0.1:" + asaasFalso.getAddress().getPort());
        registry.add("asaas.api-key", () -> "test-key");
        registry.add("asaas.webhook.token", () -> TOKEN_WEBHOOK);
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

    @AfterAll
    static void pararAsaasFalso() {
        asaasFalso.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RenovarGoldUseCase renovarGold;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PlanoRepository planoRepository;

    @Autowired
    private AssinaturaGoldRepository assinaturaGoldRepository;

    @Autowired
    private CobrancaRepository cobrancaRepository;

    @Autowired
    private DocumentoFiscalRepository documentoFiscalRepository;

    @Autowired
    private MeioPagamentoRepository meioPagamentoRepository;

    @Autowired
    private ClienteAsaasRepository clienteAsaasRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationEvents eventos;

    private UUID prestadorId;
    private Instant fimDoCiclo;

    @BeforeEach
    void prepararPrestador() {
        limparBase();
        prestadorId = usuarioRepository.save(Usuario.builder()
                .nomeCompleto("Rafael Souza")
                .email("rafael@example.com")
                .telefone("11999998888")
                .senhaHash(passwordEncoder.encode("senhaForte123"))
                .cpf("11144477735")
                .dataNascimento(LocalDate.now().minusYears(25))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(PapelUsuario.PRESTADOR)
                .statusConta(StatusConta.ATIVA)
                .telefoneConfirmadoEm(Instant.now())
                .emailConfirmadoEm(Instant.now())
                .build()).getId();
        fimDoCiclo = Instant.now().truncatedTo(ChronoUnit.MILLIS).minus(Duration.ofMinutes(10));
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private void limparBase() {
        documentoFiscalRepository.deleteAll();
        cobrancaRepository.deleteAll();
        assinaturaGoldRepository.deleteAll();
        meioPagamentoRepository.deleteAll();
        clienteAsaasRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    private UUID planoGoldId() {
        return planoRepository.findByCodigoAndAtivoTrue(Plano.CODIGO_GOLD).orElseThrow().getId();
    }

    private AssinaturaGold assinaturaAtivaVencida() {
        AssinaturaGold assinatura = AssinaturaGold.nova(prestadorId, planoGoldId(),
                fimDoCiclo.minus(Duration.ofDays(30)), fimDoCiclo);
        assinatura.confirmarAtiva();
        return assinaturaGoldRepository.save(assinatura);
    }

    /** Renovação que ficou sem resposta do gateway: a cobrança saiu, mas o LIMIO não soube do resultado. */
    private Cobranca renovacaoSemResposta(AssinaturaGold assinatura) {
        Instant inicioCicloCobrado = assinaturaGoldRepository.findById(assinatura.getId()).orElseThrow()
                .getDataFimCiclo();
        return cobrancaRepository.save(Cobranca.renovacao(assinatura.getId(), 2990, UUID.randomUUID().toString(),
                inicioCicloCobrado, 1));
    }

    private ResultActions avisar(String token, String evento, String chave) throws Exception {
        String corpo = """
                {"id":"evt_1","event":"%s","payment":{"id":"pay_fake085","status":"CONFIRMED",
                "customer":"cus_fake085","externalReference":"%s"}}""".formatted(evento, chave);
        var requisicao = post(ROTA).contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (token != null) {
            requisicao.header("asaas-access-token", token);
        }
        return mockMvc.perform(requisicao);
    }

    private AssinaturaGold recarregar(AssinaturaGold assinatura) {
        return assinaturaGoldRepository.findById(assinatura.getId()).orElseThrow();
    }

    private Cobranca recarregar(Cobranca cobranca) {
        return cobrancaRepository.findById(cobranca.getId()).orElseThrow();
    }

    private static Instant umMesDepois(Instant instante) {
        return instante.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
    }

    @Test
    void dadoRenovacaoSemResposta_quandoOAsaasConfirma_entao200RenovaEOAvisoRepetidoNaoDuplica() throws Exception {
        AssinaturaGold assinatura = assinaturaAtivaVencida();
        Cobranca cobranca = renovacaoSemResposta(assinatura);
        Instant fimAnterior = cobranca.getInicioCicloCobrado();

        avisar(TOKEN_WEBHOOK, "PAYMENT_CONFIRMED", cobranca.getChaveIdempotencia()).andExpect(status().isOk());

        assertThat(recarregar(cobranca).getStatus()).isEqualTo(StatusCobranca.APROVADA);
        assertThat(recarregar(cobranca).getGatewayCobrancaId()).isEqualTo("pay_fake085");
        assertThat(recarregar(assinatura).getDataFimCiclo()).isEqualTo(umMesDepois(fimAnterior));
        assertThat(documentoFiscalRepository.count()).isEqualTo(1);

        avisar(TOKEN_WEBHOOK, "PAYMENT_CONFIRMED", cobranca.getChaveIdempotencia()).andExpect(status().isOk());
        avisar(TOKEN_WEBHOOK, "PAYMENT_RECEIVED", cobranca.getChaveIdempotencia()).andExpect(status().isOk());

        assertThat(cobrancaRepository.count()).isEqualTo(1);
        assertThat(documentoFiscalRepository.count()).isEqualTo(1);
        assertThat(recarregar(assinatura).getDataFimCiclo()).as("o ciclo avança uma vez só")
                .isEqualTo(umMesDepois(fimAnterior));
        assertThat(eventos.stream(CobrancaGoldAprovadaEvent.class)).hasSize(1);
    }

    @Test
    void dadoRenovacaoSemResposta_quandoOAsaasRecusa_entaoAgendaNovaTentativaEAvisa() throws Exception {
        AssinaturaGold assinatura = assinaturaAtivaVencida();
        Cobranca cobranca = renovacaoSemResposta(assinatura);

        avisar(TOKEN_WEBHOOK, "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED", cobranca.getChaveIdempotencia())
                .andExpect(status().isOk());

        assertThat(recarregar(cobranca).getStatus()).isEqualTo(StatusCobranca.RECUSADA);
        AssinaturaGold aguardando = recarregar(assinatura);
        assertThat(aguardando.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(aguardando.getProximaTentativaEm()).isNotNull();
        assertThat(eventos.stream(CobrancaGoldRecusadaEvent.class)).singleElement()
                .satisfies(evento -> assertThat(evento.tentativa()).isEqualTo(1));
    }

    @Test
    void dadoAssinaturaFalsa_quandoAvisa_entao401SemProcessar() throws Exception {
        AssinaturaGold assinatura = assinaturaAtivaVencida();
        Cobranca cobranca = renovacaoSemResposta(assinatura);

        avisar("token-falso", "PAYMENT_CONFIRMED", cobranca.getChaveIdempotencia())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("WEBHOOK_INVALIDO")));
        avisar(null, "PAYMENT_CONFIRMED", cobranca.getChaveIdempotencia())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("WEBHOOK_INVALIDO")));

        assertThat(recarregar(cobranca).getStatus()).isEqualTo(StatusCobranca.PENDENTE);
        assertThat(recarregar(assinatura).getDataFimCiclo()).isEqualTo(cobranca.getInicioCicloCobrado());
        assertThat(documentoFiscalRepository.count()).isZero();
    }

    @Test
    void dadoAssinaturaQueFicouPendente_quandoOAsaasConfirma_entaoAtivaOGold() throws Exception {
        AssinaturaGold pendente = assinaturaGoldRepository.save(AssinaturaGold.nova(prestadorId, planoGoldId(),
                Instant.now(), umMesDepois(Instant.now())));
        Cobranca cobranca = cobrancaRepository.save(Cobranca.pendente(pendente.getId(), 2990, "chave-assinatura"));

        avisar(TOKEN_WEBHOOK, "PAYMENT_CONFIRMED", "chave-assinatura").andExpect(status().isOk());

        assertThat(recarregar(pendente).getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(recarregar(cobranca).getStatus()).isEqualTo(StatusCobranca.APROVADA);
        assertThat(eventos.stream(GoldAssinadoEvent.class)).hasSize(1);
    }

    @Test
    void dadoAvisoQueNaoResolveCobranca_ouDeCobrancaDesconhecida_entao200SemEfeito() throws Exception {
        AssinaturaGold assinatura = assinaturaAtivaVencida();
        Cobranca cobranca = renovacaoSemResposta(assinatura);

        avisar(TOKEN_WEBHOOK, "PAYMENT_CREATED", cobranca.getChaveIdempotencia()).andExpect(status().isOk());
        avisar(TOKEN_WEBHOOK, "PAYMENT_CONFIRMED", "chave-de-outro-sistema").andExpect(status().isOk());

        assertThat(recarregar(cobranca).getStatus()).isEqualTo(StatusCobranca.PENDENTE);
    }

    @Test
    void dadoRenovacaoPeloAsaas_quandoOAvisoChegaDepoisDaConfirmacao_entaoNadaSeRepete() throws Exception {
        AssinaturaGold assinatura = assinaturaAtivaVencida();
        meioPagamentoRepository.save(MeioPagamento.novo(prestadorId, "tok_fake_abc", "VISA", "4321"));
        Instant fimAnterior = recarregar(assinatura).getDataFimCiclo();

        renovarGold.executar();

        Cobranca cobranca = cobrancaRepository.findAll().getFirst();
        assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.APROVADA);
        assertThat(cobranca.getGatewayCobrancaId()).isEqualTo("pay_fake085");
        assertThat(recarregar(assinatura).getDataFimCiclo()).isEqualTo(umMesDepois(fimAnterior));

        avisar(TOKEN_WEBHOOK, "PAYMENT_CONFIRMED", cobranca.getChaveIdempotencia()).andExpect(status().isOk());

        assertThat(cobrancaRepository.count()).isEqualTo(1);
        assertThat(documentoFiscalRepository.count()).isEqualTo(1);
        assertThat(recarregar(assinatura).getDataFimCiclo()).isEqualTo(umMesDepois(fimAnterior));
    }
}
