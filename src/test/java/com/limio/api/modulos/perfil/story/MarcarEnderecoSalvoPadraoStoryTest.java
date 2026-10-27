package com.limio.api.modulos.perfil.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.modulos.perfil.actions.service.Geocodificador;
import com.limio.api.modulos.perfil.geo.Coordenadas;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta do TICKET-0050, Parte 5 — marcar endereço padrão (PATCH /{id}/padrao) e concorrência, endereço salvo do Empregador.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MarcarEnderecoSalvoPadraoStoryTest {

    private static final String ROTA = "/conta/perfil-empregador/enderecos";
    private static final String SENHA = "senhaForte123";

    private static final String MARIA_EMAIL = "maria@example.com";
    private static final String JOAO_EMAIL = "joao@example.com";

    @MockitoBean
    private Geocodificador geocodificador;

    @BeforeEach
    void stubGeocodificador() {
        when(geocodificador.localizarEndereco(any())).thenReturn(Optional.of(new Coordenadas(-23.5614, -46.6559)));
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
        jdbc.execute("delete from endereco_salvo");
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from verificacao_contato");
        jdbc.execute("delete from sessao");
        jdbc.execute("delete from tentativa_login");
        jdbc.execute("delete from usuario");
    }

    @Test
    void dadoOutroPadrao_quandoMarca_entaoTrocaOPadraoEDevolve200() throws Exception {
        String token = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");
        String casa = idCriado(criar(token, corpo("Casa", true)));
        String sitio = idCriado(criar(token, corpo("Sítio", false)));

        marcar(token, sitio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].id", is(sitio)))
                .andExpect(jsonPath("$[0].isPadrao", is(true)))
                .andExpect(jsonPath("$[1].id", is(casa)))
                .andExpect(jsonPath("$[1].isPadrao", is(false)));

        assertThat(padroesAtivos()).containsExactly(UUID.fromString(sitio));
        mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$[0].id", is(sitio)))
                .andExpect(jsonPath("$[1].id", is(casa)))
                .andExpect(jsonPath("$[1].isPadrao", is(false)));
    }

    @Test
    void dadoEmpregadorSemPadrao_quandoMarca_entaoPassaATerUm() throws Exception {
        String token = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");
        String casa = idCriado(criar(token, corpo("Casa", false)));

        marcar(token, casa).andExpect(status().isOk()).andExpect(jsonPath("$[0].isPadrao", is(true)));

        assertThat(padroesAtivos()).containsExactly(UUID.fromString(casa));
    }

    @Test
    void dadoEnderecoJaPadrao_quandoMarcaDeNovo_entaoContinuaPadraoSemMudarNada() throws Exception {
        String token = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");
        String casa = idCriado(criar(token, corpo("Casa", true)));

        marcar(token, casa).andExpect(status().isOk()).andExpect(jsonPath("$[0].isPadrao", is(true)));
        marcar(token, casa).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].id", is(casa)))
                .andExpect(jsonPath("$[0].isPadrao", is(true)));

        assertThat(padroesAtivos()).containsExactly(UUID.fromString(casa));
    }

    @Test
    void dadoEnderecoRemovidoOuInexistente_quandoMarca_entaoDevolve404EOPadraoAtualFicaIntacto() throws Exception {
        String token = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");
        String casa = idCriado(criar(token, corpo("Casa", true)));
        String antigo = idCriado(criar(token, corpo("Antigo", false)));
        remover(token, antigo).andExpect(status().isNoContent());

        marcar(token, antigo).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ENDERECO_NAO_ENCONTRADO")));
        marcar(token, UUID.randomUUID().toString()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ENDERECO_NAO_ENCONTRADO")));

        assertThat(padroesAtivos()).containsExactly(UUID.fromString(casa));
    }

    @Test
    void dadoEnderecoDeOutraPessoa_quandoMarca_entaoDevolve404ENadaMuda() throws Exception {
        String maria = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");
        String joao = cadastrarELogar("João Pereira", JOAO_EMAIL, "21988887777", "11144477735");
        String casa = idCriado(criar(maria, corpo("Casa", false)));
        String casaJoao = idCriado(criar(joao, corpo("Casa do João", true)));

        marcar(joao, casa).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ENDERECO_NAO_ENCONTRADO")));

        assertThat(padroesAtivos()).containsExactly(UUID.fromString(casaJoao));
    }

    @Test
    void dadoIdMalformadoOuSemToken_quandoMarca_entaoDevolve400Ou401() throws Exception {
        String token = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");

        marcar(token, "abc").andExpect(status().isBadRequest());
        mockMvc.perform(patch(ROTA + "/" + UUID.randomUUID() + "/padrao")).andExpect(status().isUnauthorized());
    }

    @Test
    void dadoVariasMarcacoesSimultaneas_quandoExecutam_entaoTodasRespondem200EFicaExatamenteUmPadrao()
            throws Exception {
        String token = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");
        int threads = 8;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            ids.add(idCriado(criar(token, corpo("Endereço " + i, i == 0))));
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> tarefas = new ArrayList<>();
            for (String id : ids) {
                tarefas.add(() -> marcar(token, id).andReturn().getResponse().getStatus());
            }
            for (Future<Integer> resultado : pool.invokeAll(tarefas)) {
                assertThat(resultado.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(padroesAtivos()).hasSize(1);
    }

    @Test
    void dadoDoisPadroesAtivosInseridosPorFora_quandoGrava_entaoOIndiceUnicoRejeita() throws Exception {
        String token = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");
        idCriado(criar(token, corpo("Casa", true)));
        String sitio = idCriado(criar(token, corpo("Sítio", false)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc
                .update("update endereco_salvo set is_padrao = true where id = ?::uuid", sitio))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ---------- helpers ----------

    private ResultActions marcar(String token, String id) throws Exception {
        return mockMvc.perform(patch(ROTA + "/" + id + "/padrao").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions remover(String token, String id) throws Exception {
        return mockMvc.perform(delete(ROTA + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions criar(String token, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post(ROTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)));
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
        corpo.put("isPadrao", padrao);
        return corpo;
    }

    private String cadastrarELogar(String nome, String email, String telefone, String cpf) throws Exception {
        mockMvc.perform(post("/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "nomeCompleto", nome,
                        "email", email,
                        "telefone", telefone,
                        "senha", SENHA,
                        "cpf", cpf,
                        "dataNascimento", "1990-01-01",
                        "cidadeUf", "São Paulo/SP"))))
                .andExpect(status().isCreated());
        String corpo = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email, "senha", SENHA, "dispositivo", "celular"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("token").asString();
    }

    private int totalEnderecos() {
        return jdbc.queryForObject("select count(*) from endereco_salvo", Integer.class);
    }

    private List<UUID> padroesAtivos() {
        return jdbc.queryForList("select id from endereco_salvo where is_padrao and removido_em is null", UUID.class);
    }
}
