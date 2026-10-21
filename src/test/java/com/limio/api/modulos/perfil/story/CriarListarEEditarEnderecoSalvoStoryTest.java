package com.limio.api.modulos.perfil.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do TICKET-0050, Parte 3 — criar, listar e editar
 * endereços salvos do Empregador. Caixa-preta em relação ao módulo {@code auth}: usuários nascem por
 * {@code POST /auth/cadastro} e fazem login por {@code POST /auth/login}; SQL só limpa a base e
 * confere o estado gravado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CriarListarEEditarEnderecoSalvoStoryTest {

    private static final String ROTA = "/conta/perfil-empregador/enderecos";
    private static final String SENHA = "senhaForte123";

    private static final Conta MARIA = new Conta("Maria da Silva", "maria@example.com", "11999998888", "52998224725");
    private static final Conta JOAO = new Conta("João Pereira", "joao@example.com", "21988887777", "11144477735");

    private record Conta(String nomeCompleto, String email, String telefone, String cpf) {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limparBase() {
        // endereco_salvo antes de perfil_empregador (FK); no @AfterEach pra não travar a limpeza de outras stories.
        jdbc.execute("delete from endereco_salvo");
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from verificacao_contato");
        jdbc.execute("delete from sessao");
        jdbc.execute("delete from tentativa_login");
        jdbc.execute("delete from usuario");
    }

    // ---------- criar ----------

    @Test
    void dadoEnderecoValido_quandoCria_entaoDevolve201EGravaTodosOsCamposComCoordenadas() throws Exception {
        String token = cadastrarELogar(MARIA);

        criar(token, corpo("Casa", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.apelido", is("Casa")))
                .andExpect(jsonPath("$.cep", is("01310100")))
                .andExpect(jsonPath("$.rua", is("Avenida Paulista")))
                .andExpect(jsonPath("$.numero", is("1000")))
                .andExpect(jsonPath("$.complemento", is("Apto 12")))
                .andExpect(jsonPath("$.bairroRegiao", is("Bela Vista")))
                .andExpect(jsonPath("$.cidade", is("São Paulo")))
                .andExpect(jsonPath("$.uf", is("SP")))
                .andExpect(jsonPath("$.latitude", is(-23.5614)))
                .andExpect(jsonPath("$.longitude", is(-46.6559)))
                .andExpect(jsonPath("$.isPadrao", is(false)))
                .andExpect(jsonPath("$.removidoEm").doesNotExist());

        Map<String, Object> linha = jdbc.queryForMap("select * from endereco_salvo");
        assertThat(linha.get("perfil_empregador_id")).isEqualTo(perfilIdDe(MARIA.email()));
        assertThat(linha.get("latitude")).isEqualTo(-23.5614);
        assertThat(linha.get("longitude")).isEqualTo(-46.6559);
        assertThat(linha.get("uf")).isEqualTo("SP");
        assertThat(linha.get("is_padrao")).isEqualTo(false);
        assertThat(linha.get("removido_em")).isNull();
    }

    @Test
    void dadoPrimeiroAcesso_quandoCria_entaoCriaOPerfilDoEmpregadorNaHora() throws Exception {
        String token = cadastrarELogar(MARIA);
        assertThat(jdbc.queryForObject("select count(*) from perfil_empregador", Integer.class)).isZero();

        criar(token, corpo("Casa", false)).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("select count(*) from perfil_empregador", Integer.class)).isEqualTo(1);
    }

    @Test
    void dadoCepComMascaraEComplementoEmBranco_quandoCria_entaoNormalizaEGravaComplementoNulo() throws Exception {
        String token = cadastrarELogar(MARIA);
        Map<String, Object> corpo = corpo("Casa", false);
        corpo.put("cep", "01310-100");
        corpo.put("complemento", "   ");

        criar(token, corpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cep", is("01310100")))
                .andExpect(jsonPath("$.complemento", is(nullValue())));
    }

    @Test
    void dadoIsPadraoAusente_quandoCria_entaoNaoEPadrao() throws Exception {
        String token = cadastrarELogar(MARIA);
        Map<String, Object> corpo = corpo("Casa", false);
        corpo.remove("isPadrao");

        criar(token, corpo).andExpect(status().isCreated()).andExpect(jsonPath("$.isPadrao", is(false)));
    }

    @Test
    void dadoPadraoExistente_quandoCriaOutroComoPadrao_entaoOAnteriorDeixaDeSer() throws Exception {
        String token = cadastrarELogar(MARIA);
        String casa = idCriado(criar(token, corpo("Casa", true)));

        String trabalho = idCriado(criar(token, corpo("Trabalho", true)));

        assertThat(padroesAtivos()).containsExactly(UUID.fromString(trabalho));
        assertThat(isPadraoNoBanco(casa)).isFalse();
    }

    @Test
    void dadoPadraoExistente_quandoCriaOutroSemPadrao_entaoOPadraoContinuaOMesmo() throws Exception {
        String token = cadastrarELogar(MARIA);
        String casa = idCriado(criar(token, corpo("Casa", true)));

        criar(token, corpo("Trabalho", false)).andExpect(status().isCreated());

        assertThat(padroesAtivos()).containsExactly(UUID.fromString(casa));
    }

    @Test
    void dadoDoisEmpregadores_quandoCadaUmCriaSeuPadrao_entaoCadaUmTemOSeu() throws Exception {
        String tokenMaria = cadastrarELogar(MARIA);
        String tokenJoao = cadastrarELogar(JOAO);

        criar(tokenMaria, corpo("Casa da Maria", true)).andExpect(status().isCreated());
        criar(tokenJoao, corpo("Casa do João", true)).andExpect(status().isCreated());

        assertThat(padroesAtivos()).hasSize(2);
    }

    // ---------- validação ----------

    @Test
    void dadoDadosInvalidos_quandoCria_entaoDevolve400ValidacaoFalhouENaoGrava() throws Exception {
        String token = cadastrarELogar(MARIA);

        for (Map<String, Object> invalido : List.of(
                com(corpo("Casa", false), "cep", "1234567"),
                com(corpo("Casa", false), "cep", "abcdefgh"),
                semCampo(corpo("Casa", false), "cep"),
                com(corpo("Casa", false), "uf", "XX"),
                com(corpo("Casa", false), "uf", "sp"),
                semCampo(corpo("Casa", false), "uf"),
                com(corpo("Casa", false), "latitude", 90.1),
                com(corpo("Casa", false), "longitude", -180.1),
                semCampo(corpo("Casa", false), "latitude"),
                semCampo(corpo("Casa", false), "longitude"),
                com(corpo("Casa", false), "apelido", "  "),
                com(corpo("Casa", false), "rua", ""),
                com(corpo("Casa", false), "numero", " "),
                com(corpo("Casa", false), "bairroRegiao", ""),
                com(corpo("Casa", false), "cidade", ""),
                com(corpo("Casa", false), "apelido", "a".repeat(61)))) {
            criar(token, invalido)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        }

        assertThat(totalEnderecos()).isZero();
    }

    @Test
    void dadoJsonMalformado_quandoCria_entaoDevolve400() throws Exception {
        String token = cadastrarELogar(MARIA);

        mockMvc.perform(post(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{ nao e json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    // ---------- listar ----------

    @Test
    void dadoSemEnderecos_quandoLista_entaoDevolveListaVazia() throws Exception {
        String token = cadastrarELogar(MARIA);

        listar(token).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void dadoEnderecos_quandoLista_entaoPadraoVemPrimeiroDepoisPorApelido() throws Exception {
        String token = cadastrarELogar(MARIA);
        criar(token, corpo("Trabalho", false)).andExpect(status().isCreated());
        criar(token, corpo("Casa", false)).andExpect(status().isCreated());
        criar(token, corpo("Sítio", true)).andExpect(status().isCreated());

        listar(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].apelido", is("Sítio")))
                .andExpect(jsonPath("$[0].isPadrao", is(true)))
                .andExpect(jsonPath("$[1].apelido", is("Casa")))
                .andExpect(jsonPath("$[2].apelido", is("Trabalho")));
    }

    @Test
    void dadoEnderecoRemovido_quandoLista_entaoNaoAparece() throws Exception {
        String token = cadastrarELogar(MARIA);
        criar(token, corpo("Casa", false)).andExpect(status().isCreated());
        String antigo = idCriado(criar(token, corpo("Antigo", false)));
        jdbc.update("update endereco_salvo set removido_em = now() where id = ?::uuid", antigo);

        listar(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].apelido", is("Casa")));
        assertThat(totalEnderecos()).isEqualTo(2);
    }

    @Test
    void dadoDoisEmpregadores_quandoCadaUmLista_entaoVeSoOsProprios() throws Exception {
        String tokenMaria = cadastrarELogar(MARIA);
        String tokenJoao = cadastrarELogar(JOAO);
        criar(tokenMaria, corpo("Casa da Maria", false)).andExpect(status().isCreated());
        criar(tokenJoao, corpo("Casa do João", false)).andExpect(status().isCreated());

        listar(tokenMaria).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].apelido", is("Casa da Maria")));
        listar(tokenJoao).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].apelido", is("Casa do João")));
    }

    // ---------- editar ----------

    @Test
    void dadoEnderecoProprio_quandoEdita_entaoSubstituiTodosOsCamposMantendoOId() throws Exception {
        String token = cadastrarELogar(MARIA);
        String id = idCriado(criar(token, corpo("Casa", false)));
        Map<String, Object> novo = corpo("Escritório", false);
        novo.put("cep", "20040-020");
        novo.put("rua", "Rua da Assembleia");
        novo.put("numero", "S/N");
        novo.remove("complemento");
        novo.put("bairroRegiao", "Centro");
        novo.put("cidade", "Rio de Janeiro");
        novo.put("uf", "RJ");
        novo.put("latitude", -22.9068);
        novo.put("longitude", -43.1729);

        editar(token, id, novo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.apelido", is("Escritório")))
                .andExpect(jsonPath("$.cep", is("20040020")))
                .andExpect(jsonPath("$.rua", is("Rua da Assembleia")))
                .andExpect(jsonPath("$.numero", is("S/N")))
                .andExpect(jsonPath("$.complemento", is(nullValue())))
                .andExpect(jsonPath("$.uf", is("RJ")))
                .andExpect(jsonPath("$.latitude", is(-22.9068)))
                .andExpect(jsonPath("$.longitude", is(-43.1729)));

        Map<String, Object> linha = jdbc.queryForMap("select * from endereco_salvo where id = ?::uuid", id);
        assertThat(linha.get("apelido")).isEqualTo("Escritório");
        assertThat(linha.get("uf")).isEqualTo("RJ");
        assertThat(linha.get("latitude")).isEqualTo(-22.9068);
        assertThat(linha.get("longitude")).isEqualTo(-43.1729);
        assertThat(linha.get("complemento")).isNull();
        assertThat(totalEnderecos()).isEqualTo(1);
    }

    @Test
    void dadoOutroEnderecoPadrao_quandoEditaMarcandoPadrao_entaoOAnteriorDeixaDeSer() throws Exception {
        String token = cadastrarELogar(MARIA);
        String casa = idCriado(criar(token, corpo("Casa", true)));
        String trabalho = idCriado(criar(token, corpo("Trabalho", false)));

        editar(token, trabalho, corpo("Trabalho", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPadrao", is(true)));

        assertThat(padroesAtivos()).containsExactly(UUID.fromString(trabalho));
        assertThat(isPadraoNoBanco(casa)).isFalse();
    }

    @Test
    void dadoEnderecoPadrao_quandoEditaMantendoPadrao_entaoContinuaPadrao() throws Exception {
        String token = cadastrarELogar(MARIA);
        String casa = idCriado(criar(token, corpo("Casa", true)));

        editar(token, casa, corpo("Casa nova", true)).andExpect(status().isOk());

        assertThat(padroesAtivos()).containsExactly(UUID.fromString(casa));
    }

    @Test
    void dadoEnderecoPadrao_quandoEditaSemPadrao_entaoEmpregadorFicaSemPadrao() throws Exception {
        String token = cadastrarELogar(MARIA);
        String casa = idCriado(criar(token, corpo("Casa", true)));

        editar(token, casa, corpo("Casa", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPadrao", is(false)));

        assertThat(padroesAtivos()).isEmpty();
    }

    @Test
    void dadoEnderecoDeOutraPessoa_quandoEdita_entaoDevolve404ENadaMuda() throws Exception {
        String tokenMaria = cadastrarELogar(MARIA);
        String tokenJoao = cadastrarELogar(JOAO);
        String deMaria = idCriado(criar(tokenMaria, corpo("Casa da Maria", true)));
        criar(tokenJoao, corpo("Casa do João", true)).andExpect(status().isCreated());

        editar(tokenJoao, deMaria, corpo("Invadido", true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ENDERECO_NAO_ENCONTRADO")));

        assertThat(jdbc.queryForObject("select apelido from endereco_salvo where id = ?::uuid", String.class, deMaria))
                .isEqualTo("Casa da Maria");
        assertThat(padroesAtivos()).hasSize(2);
        assertThat(isPadraoNoBanco(deMaria)).isTrue();
    }

    @Test
    void dadoIdInexistenteOuRemovido_quandoEdita_entaoDevolve404EDesfazODesmarcarDoPadrao() throws Exception {
        String token = cadastrarELogar(MARIA);
        String casa = idCriado(criar(token, corpo("Casa", true)));
        String removido = idCriado(criar(token, corpo("Antigo", false)));
        jdbc.update("update endereco_salvo set removido_em = now() where id = ?::uuid", removido);

        editar(token, UUID.randomUUID().toString(), corpo("X", true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ENDERECO_NAO_ENCONTRADO")));
        editar(token, removido, corpo("X", true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ENDERECO_NAO_ENCONTRADO")));

        assertThat(padroesAtivos()).containsExactly(UUID.fromString(casa));
        assertThat(jdbc.queryForObject("select apelido from endereco_salvo where id = ?::uuid", String.class, removido))
                .isEqualTo("Antigo");
    }

    @Test
    void dadoDadosInvalidos_quandoEdita_entaoDevolve400ENadaMuda() throws Exception {
        String token = cadastrarELogar(MARIA);
        String id = idCriado(criar(token, corpo("Casa", true)));

        editar(token, id, com(corpo("Casa", false), "uf", "XX"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        editar(token, id, com(corpo("Casa", false), "cep", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        assertThat(isPadraoNoBanco(id)).isTrue();
    }

    @Test
    void dadoIdMalformado_quandoEdita_entaoDevolve400() throws Exception {
        String token = cadastrarELogar(MARIA);

        editar(token, "nao-e-uuid", corpo("Casa", false)).andExpect(status().isBadRequest());
    }

    // ---------- autenticação ----------

    @Test
    void dadoSemToken_quandoListaCriaOuEdita_entaoDevolve401() throws Exception {
        mockMvc.perform(get(ROTA)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo("Casa", false))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(ROTA + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo("Casa", false))))
                .andExpect(status().isUnauthorized());
    }

    // ---------- concorrência ----------

    @Test
    void dadoVariasCriacoesSimultaneasComoPadrao_quandoExecutam_entaoTodasCriamEFicaExatamenteUmPadrao()
            throws Exception {
        String token = cadastrarELogar(MARIA);
        criar(token, corpo("Semente", false)).andExpect(status().isCreated()); // garante o perfil antes da corrida
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> tarefas = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String apelido = "Concorrente " + i;
                tarefas.add(() -> criar(token, corpo(apelido, true)).andReturn().getResponse().getStatus());
            }
            for (Future<Integer> resultado : pool.invokeAll(tarefas)) {
                assertThat(resultado.get()).isEqualTo(201);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(totalEnderecos()).isEqualTo(threads + 1);
        assertThat(padroesAtivos()).hasSize(1);
    }

    // ---------- helpers ----------

    private ResultActions criar(String token, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post(ROTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)));
    }

    private ResultActions editar(String token, String id, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(put(ROTA + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)));
    }

    private ResultActions listar(String token) throws Exception {
        return mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String idCriado(ResultActions criacao) throws Exception {
        String json = criacao.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asString();
    }

    private static Map<String, Object> corpo(String apelido, boolean padrao) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("apelido", apelido);
        corpo.put("cep", "01310100");
        corpo.put("rua", "Avenida Paulista");
        corpo.put("numero", "1000");
        corpo.put("complemento", "Apto 12");
        corpo.put("bairroRegiao", "Bela Vista");
        corpo.put("cidade", "São Paulo");
        corpo.put("uf", "SP");
        corpo.put("latitude", -23.5614);
        corpo.put("longitude", -46.6559);
        corpo.put("isPadrao", padrao);
        return corpo;
    }

    private static Map<String, Object> com(Map<String, Object> corpo, String campo, Object valor) {
        corpo.put(campo, valor);
        return corpo;
    }

    private static Map<String, Object> semCampo(Map<String, Object> corpo, String campo) {
        corpo.remove(campo);
        return corpo;
    }

    private String cadastrarELogar(Conta conta) throws Exception {
        mockMvc.perform(post("/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "nomeCompleto", conta.nomeCompleto(),
                        "email", conta.email(),
                        "telefone", conta.telefone(),
                        "senha", SENHA,
                        "cpf", conta.cpf(),
                        "dataNascimento", "1990-01-01",
                        "cidadeUf", "São Paulo/SP"))))
                .andExpect(status().isCreated());
        String corpo = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", conta.email(), "senha", SENHA, "dispositivo", "celular"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(corpo);
        return json.get("token").asString();
    }

    private UUID perfilIdDe(String email) {
        return jdbc.queryForObject("""
                select p.id from perfil_empregador p join usuario u on u.id = p.usuario_id where u.email = ?
                """, UUID.class, email);
    }

    private List<UUID> padroesAtivos() {
        return jdbc.queryForList("select id from endereco_salvo where is_padrao and removido_em is null", UUID.class);
    }

    private boolean isPadraoNoBanco(String id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("select is_padrao from endereco_salvo where id = ?::uuid", Boolean.class, id));
    }

    private int totalEnderecos() {
        return jdbc.queryForObject("select count(*) from endereco_salvo", Integer.class);
    }
}
