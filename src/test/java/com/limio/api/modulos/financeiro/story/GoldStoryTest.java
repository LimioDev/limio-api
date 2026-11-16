package com.limio.api.modulos.financeiro.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
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
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldRepository;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.CobrancaRepository;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal.DocumentoFiscalRepository;
import com.limio.api.modulos.financeiro.assinaturagold.enums.StatusAssinatura;
import com.limio.api.modulos.financeiro.eventos.GoldAssinadoEvent;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamentoRepository;
import com.limio.api.modulos.financeiro.plano.Plano;
import com.limio.api.modulos.financeiro.plano.PlanoRepository;
import com.limio.api.modulos.financeiro.records.AssinarGoldRequest;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) de "Assinar o plano GOLD" (ETI-81), gateway simulado por
 * {@code GatewayPagamentoSimulado} (tokens {@code tok_recusado}/{@code tok_indisponivel} controlam o
 * resultado — ver {@code application.properties}).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class GoldStoryTest {

    private static final String SENHA = "senhaForte123";
    private static final String TOKEN_APROVADO = "tok_qualquer";
    private static final String TOKEN_RECUSADO = "tok_recusado";
    private static final String TOKEN_INDISPONIVEL = "tok_indisponivel";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SessaoRepository sessaoRepository;

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
    private com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoRepository verificacaoContatoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock relogio;

    @Autowired
    private ApplicationEvents eventos;

    @BeforeEach
    void limparBase() {
        documentoFiscalRepository.deleteAll();
        cobrancaRepository.deleteAll();
        assinaturaGoldRepository.deleteAll();
        meioPagamentoRepository.deleteAll();
        // TrocarEmailETelefoneStoryTest só limpa verificacao_contato no @BeforeEach, não no @AfterEach — se
        // esta classe rodar depois, uma linha órfã sobra e o deleteAll de usuario abaixo viola a FK.
        verificacaoContatoRepository.deleteAll();
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

    private Usuario empregador(String nome, String email, String cpf) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nome)
                .email(email)
                .telefone("11988887777")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(30))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(StatusConta.ATIVA)
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

    private ResultActions assinar(String token, String gatewayToken) throws Exception {
        String corpo = objectMapper.writeValueAsString(new AssinarGoldRequest(Plano.CODIGO_GOLD, gatewayToken));
        return mockMvc.perform(post("/financeiro/gold/assinatura")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions consultar(String token) throws Exception {
        return mockMvc.perform(get("/financeiro/gold")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void dadoPrestadorFree_quandoConsultaGold_entao200SemCartaoSalvo() throws Exception {
        prestador("Rafael Souza", "rafael@example.com", "11144477735");

        consultar(logar("rafael@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoPlano", is("GOLD")))
                .andExpect(jsonPath("$.cartaoSalvo", nullValue()));
    }

    @Test
    void dadoPagamentoAprovado_quandoAssina_entao201EAtivaGold() throws Exception {
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "11144477735");
        String token = logar("rafael@example.com");

        assinar(token, TOKEN_APROVADO)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ATIVA")));

        AssinaturaGold assinatura = assinaturaGoldRepository.findByUsuarioId(rafael.getId()).orElseThrow();
        assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(cobrancaRepository.findAll()).hasSize(1);
        assertThat(documentoFiscalRepository.findAll()).hasSize(1);
        assertThat(meioPagamentoRepository.findByUsuarioId(rafael.getId())).isPresent();

        List<GoldAssinadoEvent> publicados = eventos.stream(GoldAssinadoEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).usuarioId()).isEqualTo(rafael.getId());

        consultar(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartaoSalvo.bandeira", is("VISA")));
    }

    @Test
    void dadoPagamentoRecusado_quandoAssina_entao402ECancelaAPendencia() throws Exception {
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "11144477735");
        String token = logar("rafael@example.com");

        assinar(token, TOKEN_RECUSADO)
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.codigo", is("PAGAMENTO_RECUSADO")));

        AssinaturaGold assinatura = assinaturaGoldRepository.findByUsuarioId(rafael.getId()).orElseThrow();
        assertThat(assinatura.estaAtiva()).isFalse();
        assertThat(cobrancaRepository.findAll()).hasSize(1);
        assertThat(meioPagamentoRepository.findByUsuarioId(rafael.getId())).isEmpty();
        assertThat(documentoFiscalRepository.findAll()).isEmpty();
    }

    @Test
    void dadoGatewayIndisponivel_quandoAssina_entao503() throws Exception {
        prestador("Rafael Souza", "rafael@example.com", "11144477735");
        String token = logar("rafael@example.com");

        assinar(token, TOKEN_INDISPONIVEL)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo", is("GATEWAY_INDISPONIVEL")));
    }

    @Test
    void dadoPrestadorJaGold_quandoAssinaDeNovo_entao409AssinaturaJaAtiva() throws Exception {
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "11144477735");
        Plano gold = planoRepository.findByCodigoAndAtivoTrue(Plano.CODIGO_GOLD).orElseThrow();
        Instant agora = relogio.instant();
        AssinaturaGold ativa = AssinaturaGold.nova(rafael.getId(), gold.getId(), agora,
                agora.atZone(ZoneOffset.UTC).plusMonths(1).toInstant());
        ativa.confirmarAtiva();
        assinaturaGoldRepository.save(ativa);

        assinar(logar("rafael@example.com"), TOKEN_APROVADO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ASSINATURA_JA_ATIVA")));

        assertThat(cobrancaRepository.findAll()).isEmpty();
    }

    @Test
    void dadoCobrancaPendenteDoMesmoPrestador_quandoAssinaDeNovo_entao409CobrancaEmAndamento() throws Exception {
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "11144477735");
        Plano gold = planoRepository.findByCodigoAndAtivoTrue(Plano.CODIGO_GOLD).orElseThrow();
        Instant agora = relogio.instant();
        assinaturaGoldRepository.save(AssinaturaGold.nova(rafael.getId(), gold.getId(), agora,
                agora.atZone(ZoneOffset.UTC).plusMonths(1).toInstant()));

        assinar(logar("rafael@example.com"), TOKEN_APROVADO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("COBRANCA_EM_ANDAMENTO")));

        assertThat(cobrancaRepository.findAll()).isEmpty();
    }

    @Test
    void dadoPapelAtivoEmpregador_quandoAssina_entao403AcessoNegado() throws Exception {
        empregador("Maria da Silva", "maria@example.com", "52998224725");

        assinar(logar("maria@example.com"), TOKEN_APROVADO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
    }

    @Test
    void dadoSemToken_quandoAssina_entao401() throws Exception {
        mockMvc.perform(post("/financeiro/gold/assinatura")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssinarGoldRequest(Plano.CODIGO_GOLD, TOKEN_APROVADO))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }

    /**
     * Clique duplo: duas requisições simultâneas pro mesmo Prestador. A trava transacional
     * (EscopoTrava.ASSINATURA_GOLD) serializa as duas; a segunda encontra a assinatura já ATIVA (ETI-81).
     */
    @Test
    void dadoDuasAssinaturasSimultaneasDoMesmoPrestador_entaoSoUmaCobraNoGateway() throws Exception {
        prestador("Rafael Souza", "rafael@example.com", "11144477735");
        String token = logar("rafael@example.com");

        List<Callable<Integer>> tarefas = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tarefas.add(() -> assinar(token, TOKEN_APROVADO).andReturn().getResponse().getStatus());
        }
        List<Integer> status = emParalelo(tarefas);

        assertThat(status.stream().filter(s -> s == 201).toList()).hasSize(1);
        assertThat(status.stream().filter(s -> s == 409).toList()).hasSize(1);
        assertThat(assinaturaGoldRepository.findAll()).hasSize(1);
        assertThat(cobrancaRepository.findAll()).hasSize(1);
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
}
