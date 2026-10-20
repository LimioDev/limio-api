package com.limio.api.modulos.perfil.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.seguranca.JwtAuthFilter;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do TICKET-0048 — consultar o
 * perfil de Empregador (com os dados reais do usuário, lidos pela porta de
 * {@code comum/}) e editar o nome de exibição. Caixa-preta em relação ao módulo
 * {@code auth}: usuários nascem por {@code POST /auth/cadastro} e fazem login por
 * {@code POST /auth/login}; só a limpeza e o ajuste de datas usam SQL.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConsultarEEditarPerfilEmpregadorStoryTest {

    private static final String ROTA = "/conta/perfil-empregador";
    private static final String ROTA_NOME = ROTA + "/nome-exibicao";
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

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void limparBase() {
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from verificacao_contato");
        jdbc.execute("delete from sessao");
        jdbc.execute("delete from tentativa_login");
        jdbc.execute("delete from usuario");
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

    private ResultActions consultar(String token) throws Exception {
        return mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions editarNome(String token, String nome) throws Exception {
        return mockMvc.perform(patch(ROTA_NOME)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("nomeExibicao", nome))));
    }

    private UUID usuarioIdDe(String email) {
        return jdbc.queryForObject("select id from usuario where email = ?", UUID.class, email);
    }

    private int perfisDe(UUID usuarioId) {
        return jdbc.queryForObject("select count(*) from perfil_empregador where usuario_id = ?", Integer.class,
                usuarioId);
    }

    private String nomeExibicaoDe(UUID usuarioId) {
        return jdbc.queryForObject("select nome_exibicao from perfil_empregador where usuario_id = ?", String.class,
                usuarioId);
    }

    private static int totalPerfis(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select count(*) from perfil_empregador", Integer.class);
    }

    /** Token válido de um usuário que não existe — simula conta removida com token ainda vivo. */
    private String tokenDeUsuarioInexistente() {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .claim(JwtAuthFilter.CLAIM_PAPEL, "EMPREGADOR")
                .issuedAt(agora)
                .expiresAt(agora.plus(Duration.ofMinutes(15)))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void dadoTrocaDeEmailETelefonePeloAuth_quandoConsultaOPerfil_entaoVeOsNovosContatosSemConfirmacao()
            throws Exception {
        String token = cadastrarELogar(MARIA);
        jdbc.update("update usuario set email_confirmado_em = now(), telefone_confirmado_em = now() where email = ?",
                MARIA.email());
        consultar(token)
                .andExpect(jsonPath("$.emailConfirmadoEm", notNullValue()))
                .andExpect(jsonPath("$.telefoneConfirmadoEm", notNullValue()));

        mockMvc.perform(patch("/auth/email")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("novoEmail", "maria.nova@teste.com",
                        "senhaAtual", SENHA)))).andExpect(status().isOk());
        mockMvc.perform(patch("/auth/telefone")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("novoTelefone", "(21) 98888-7777",
                        "senhaAtual", SENHA)))).andExpect(status().isOk());

        consultar(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("maria.nova@teste.com")))
                .andExpect(jsonPath("$.telefone", is("+5521988887777")))
                .andExpect(jsonPath("$.emailConfirmadoEm", nullValue()))
                .andExpect(jsonPath("$.telefoneConfirmadoEm", nullValue()))
                .andExpect(jsonPath("$.nomeExibicao", is("Maria da Silva")));
    }

    @Test
    void dadoUsuarioAutenticado_quandoConsulta_entaoDevolveDadosReaisDoCadastro() throws Exception {
        String token = cadastrarELogar(MARIA);

        consultar(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("Maria da Silva")))
                .andExpect(jsonPath("$.email", is("maria@example.com")))
                .andExpect(jsonPath("$.telefone", is("+5511999998888")))
                .andExpect(jsonPath("$.emailConfirmadoEm", nullValue()))
                .andExpect(jsonPath("$.telefoneConfirmadoEm", nullValue()))
                .andExpect(jsonPath("$.mediaAvaliacoes", nullValue()))
                .andExpect(jsonPath("$.qtdAvaliacoes", is(0)));
    }

    @Test
    void dadoContatoConfirmado_quandoConsulta_entaoDevolveAsDatasDeConfirmacao() throws Exception {
        String token = cadastrarELogar(MARIA);
        jdbc.update("update usuario set email_confirmado_em = now(), telefone_confirmado_em = now() where email = ?",
                MARIA.email());

        consultar(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailConfirmadoEm", notNullValue()))
                .andExpect(jsonPath("$.telefoneConfirmadoEm", notNullValue()));
    }

    @Test
    void dadoSemToken_quandoConsultaOuEdita_entao401() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
        mockMvc.perform(patch(ROTA_NOME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("nomeExibicao", "Maria"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dadoDoisUsuarios_quandoCadaUmConsulta_entaoVeSoOsProprioDados() throws Exception {
        String tokenMaria = cadastrarELogar(MARIA);
        String tokenJoao = cadastrarELogar(JOAO);

        consultar(tokenMaria)
                .andExpect(jsonPath("$.nomeExibicao", is("Maria da Silva")))
                .andExpect(jsonPath("$.email", is("maria@example.com")))
                .andExpect(jsonPath("$.telefone", is("+5511999998888")));
        consultar(tokenJoao)
                .andExpect(jsonPath("$.nomeExibicao", is("João Pereira")))
                .andExpect(jsonPath("$.email", is("joao@example.com")))
                .andExpect(jsonPath("$.telefone", is("+5521988887777")));
    }

    @Test
    void dadoDoisUsuarios_quandoUmEditaNome_entaoOutroNaoMuda() throws Exception {
        String tokenMaria = cadastrarELogar(MARIA);
        String tokenJoao = cadastrarELogar(JOAO);
        consultar(tokenJoao).andExpect(status().isOk());

        editarNome(tokenMaria, "Maria Silva")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("Maria Silva")))
                .andExpect(jsonPath("$.email", is("maria@example.com")));

        consultar(tokenJoao).andExpect(jsonPath("$.nomeExibicao", is("João Pereira")));
        assertThat(nomeExibicaoDe(usuarioIdDe(JOAO.email()))).isEqualTo("João Pereira");
    }

    @Test
    void dadoUsuarioSemPerfil_quandoConsultaPelaPrimeiraVez_entaoCriaPerfilComNomeCompleto() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID usuarioId = usuarioIdDe(MARIA.email());
        assertThat(perfisDe(usuarioId)).isZero();

        consultar(token).andExpect(status().isOk());

        assertThat(perfisDe(usuarioId)).isEqualTo(1);
        assertThat(nomeExibicaoDe(usuarioId)).isEqualTo("Maria da Silva");
        assertThat(jdbc.queryForObject("select qtd_avaliacoes from perfil_empregador where usuario_id = ?",
                Integer.class, usuarioId)).isZero();
        assertThat(jdbc.queryForObject("select media_avaliacoes from perfil_empregador where usuario_id = ?",
                java.math.BigDecimal.class, usuarioId)).isNull();
    }

    @Test
    void dadoPerfilJaCriado_quandoConsultaDeNovo_entaoNaoRecriaNemSobrescreveONome() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID usuarioId = usuarioIdDe(MARIA.email());
        consultar(token).andExpect(status().isOk());
        jdbc.update("update usuario set nome_completo = 'Outro Nome' where email = ?", MARIA.email());

        consultar(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("Maria da Silva")));

        assertThat(perfisDe(usuarioId)).isEqualTo(1);
    }

    @Test
    void dadoNomeCompletoMaiorQueOLimite_quandoConsultaPelaPrimeiraVez_entaoNomeDeExibicaoCabeNoLimite()
            throws Exception {
        String token = cadastrarELogar(MARIA);
        jdbc.update("update usuario set nome_completo = ? where email = ?", "a".repeat(120), MARIA.email());

        consultar(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("a".repeat(PerfilEmpregador.NOME_EXIBICAO_MAX))));
    }

    @Test
    void dadoUsuarioSemPerfil_quandoEditaNome_entaoCriaPerfilEGravaONomePedido() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID usuarioId = usuarioIdDe(MARIA.email());

        editarNome(token, "  Mari  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("Mari")))
                .andExpect(jsonPath("$.email", is("maria@example.com")));

        assertThat(perfisDe(usuarioId)).isEqualTo(1);
        assertThat(nomeExibicaoDe(usuarioId)).isEqualTo("Mari");
    }

    @Test
    void dadoPrimeiroAcessoSimultaneo_quandoVariasConsultas_entaoCriaUmUnicoPerfil() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID usuarioId = usuarioIdDe(MARIA.email());
        int chamadas = 8;

        ExecutorService pool = Executors.newFixedThreadPool(chamadas);
        try {
            List<Callable<Integer>> tarefas = new ArrayList<>();
            for (int i = 0; i < chamadas; i++) {
                tarefas.add(() -> consultar(token).andReturn().getResponse().getStatus());
            }
            for (Future<Integer> resultado : pool.invokeAll(tarefas)) {
                assertThat(resultado.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(perfisDe(usuarioId)).isEqualTo(1);
    }

    @Test
    void dadoContaEncerrada_quandoConsultaOuEdita_entao404SemCriarPerfil() throws Exception {
        String token = cadastrarELogar(MARIA);
        jdbc.update("update usuario set anonimizado_em = now() where email = ?", MARIA.email());

        consultar(token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("PERFIL_EMPREGADOR_NAO_ENCONTRADO")));
        editarNome(token, "Maria")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("PERFIL_EMPREGADOR_NAO_ENCONTRADO")));

        assertThat(totalPerfis(jdbc)).isZero();
    }

    @Test
    void dadoTokenDeUsuarioInexistente_quandoConsultaOuEdita_entao404SemCriarPerfil() throws Exception {
        String token = tokenDeUsuarioInexistente();

        consultar(token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("PERFIL_EMPREGADOR_NAO_ENCONTRADO")));
        editarNome(token, "Maria").andExpect(status().isNotFound());

        assertThat(totalPerfis(jdbc)).isZero();
    }

    @Test
    void dadoNomeEmBrancoOuGrande_quandoEditaNome_entao400ENomeNaoMuda() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID usuarioId = usuarioIdDe(MARIA.email());
        consultar(token).andExpect(status().isOk());

        editarNome(token, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        editarNome(token, "a".repeat(PerfilEmpregador.NOME_EXIBICAO_MAX + 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        assertThat(nomeExibicaoDe(usuarioId)).isEqualTo("Maria da Silva");
    }
}
