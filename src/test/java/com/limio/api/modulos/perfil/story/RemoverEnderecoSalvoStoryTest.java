package com.limio.api.modulos.perfil.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import com.limio.api.modulos.perfil.perfilprestador.Coordenadas;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta do TICKET-0050, Parte 4 — remover (soft delete) endereço salvo do Empregador.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RemoverEnderecoSalvoStoryTest {

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
    void dadoEnderecoDoDono_quandoRemove_entaoDevolve204SomeDaListaEALinhaPermaneceComRemovidoEm() throws Exception {
        String token = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");
        String casa = idCriado(criar(token, corpo("Casa", false)));
        String sitio = idCriado(criar(token, corpo("Sítio", false)));

        remover(token, casa).andExpect(status().isNoContent());

        mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(sitio)));
        Map<String, Object> linha = jdbc.queryForMap("select * from endereco_salvo where id = ?::uuid", casa);
        assertThat(linha.get("removido_em")).isNotNull();
        assertThat(linha.get("apelido")).isEqualTo("Casa");
        assertThat(totalEnderecos()).isEqualTo(2);
    }

    @Test
    void dadoEnderecoPadrao_quandoRemove_entaoLimpaOPadraoESemPromocaoAutomatica() throws Exception {
        String token = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");
        String casa = idCriado(criar(token, corpo("Casa", true)));
        String sitio = idCriado(criar(token, corpo("Sítio", false)));

        remover(token, casa).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select is_padrao from endereco_salvo where id = ?::uuid", Boolean.class, casa))
                .isFalse();
        mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(sitio)))
                .andExpect(jsonPath("$[0].isPadrao", is(false)));
    }

    @Test
    void dadoPadraoRemovido_quandoCriaNovoPadrao_entaoNaoConflitaComOIndiceUnico() throws Exception {
        String token = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");
        String casa = idCriado(criar(token, corpo("Casa", true)));
        remover(token, casa).andExpect(status().isNoContent());

        String novo = idCriado(criar(token, corpo("Novo", true)));

        assertThat(jdbc.queryForList("select id from endereco_salvo where is_padrao and removido_em is null",
                UUID.class)).containsExactly(UUID.fromString(novo));
    }

    @Test
    void dadoEnderecoJaRemovidoOuInexistente_quandoRemove_entaoDevolve404() throws Exception {
        String token = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");
        String casa = idCriado(criar(token, corpo("Casa", false)));
        remover(token, casa).andExpect(status().isNoContent());
        Object removidoEm = jdbc.queryForObject("select removido_em from endereco_salvo where id = ?::uuid",
                java.sql.Timestamp.class, casa);

        remover(token, casa)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ENDERECO_NAO_ENCONTRADO")));
        remover(token, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ENDERECO_NAO_ENCONTRADO")));

        assertThat(jdbc.queryForObject("select removido_em from endereco_salvo where id = ?::uuid",
                java.sql.Timestamp.class, casa)).isEqualTo(removidoEm);
    }

    @Test
    void dadoEnderecoDeOutraPessoa_quandoRemove_entaoDevolve404ENadaMuda() throws Exception {
        String maria = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");
        String joao = cadastrarELogar("João Pereira", JOAO_EMAIL, "21988887777", "11144477735");
        String casa = idCriado(criar(maria, corpo("Casa", true)));

        remover(joao, casa)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ENDERECO_NAO_ENCONTRADO")));

        assertThat(jdbc.queryForObject("select removido_em from endereco_salvo where id = ?::uuid",
                java.sql.Timestamp.class, casa)).isNull();
        assertThat(jdbc.queryForObject("select is_padrao from endereco_salvo where id = ?::uuid", Boolean.class, casa))
                .isTrue();
    }

    @Test
    void dadoIdMalformado_quandoRemove_entaoDevolve400() throws Exception {
        String token = cadastrarELogar("Maria da Silva", MARIA_EMAIL, "11999998888", "52998224725");

        remover(token, "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoSemToken_quandoRemove_entaoDevolve401() throws Exception {
        mockMvc.perform(delete(ROTA + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

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
}
