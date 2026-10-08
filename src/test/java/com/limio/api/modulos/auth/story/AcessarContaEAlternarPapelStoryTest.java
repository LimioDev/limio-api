package com.limio.api.modulos.auth.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;

import tools.jackson.databind.ObjectMapper;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.actions.helper.RefreshTokenHelper;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.records.RefreshTokenRequest;
import com.limio.api.modulos.auth.sessao.Sessao;
import com.limio.api.modulos.auth.sessao.SessaoRepository;
import com.limio.api.modulos.auth.tentativalogin.TentativaLoginRepository;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

/**
 * BDD ponta a ponta (controller -> banco real) do TICKET-0032 — login (UC03),
 * renovação de sessão, logout do aparelho (UC08) e troca de papel ativo (UC09).
 * Sobe Spring context + Testcontainers Postgres.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AcessarContaEAlternarPapelStoryTest {

    private static final String EMAIL = "maria@example.com";
    private static final String SENHA = "senhaForte123";
    private static final String OUTRO_EMAIL = "joao@example.com";
    private static final String OUTRO_CPF = "11144477735";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SessaoRepository sessaoRepository;

    @Autowired
    private TentativaLoginRepository tentativaLoginRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void limparBase() {
        sessaoRepository.deleteAll();
        tentativaLoginRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // Os outros stories apagam usuario sem saber de sessao (FK pra usuario): não deixar sessão pra trás.
    @AfterEach
    void naoDeixarSessaoPraTras() {
        limparBase();
    }

    private Usuario cadastrar(StatusConta status) {
        return cadastrar(EMAIL, "52998224725", status);
    }

    private Usuario cadastrar(String email, String cpf, StatusConta status) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto("Maria da Silva")
                .email(email)
                .telefone("11999998888")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(25))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(status)
                .build());
    }

    private ResultActions login(String email, String senha, String dispositivo) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, senha, dispositivo))));
    }

    private LoginResponse logado(String dispositivo) throws Exception {
        return logadoComo(EMAIL, dispositivo);
    }

    private LoginResponse logadoComo(String email, String dispositivo) throws Exception {
        return lerTokens(login(email, SENHA, dispositivo).andExpect(status().isOk()));
    }

    private ResultActions renovar(String refreshToken) throws Exception {
        return mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))));
    }

    private ResultActions logout(LoginResponse sessao) throws Exception {
        return mockMvc.perform(post("/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessao.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(sessao.refreshToken()))));
    }

    private ResultActions trocarPapel(String tokenAcesso, String papel) throws Exception {
        return mockMvc.perform(patch("/auth/papel-ativo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAcesso)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"papel\":\"" + papel + "\"}"));
    }

    private LoginResponse lerTokens(ResultActions resultado) throws Exception {
        return objectMapper.readValue(resultado.andReturn().getResponse().getContentAsString(), LoginResponse.class);
    }

    private Sessao sessaoDo(LoginResponse tokens) {
        return sessaoRepository.findAll().stream()
                .filter(s -> s.getRefreshTokenHash().equals(RefreshTokenHelper.hash(tokens.refreshToken())))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void dadoContaAtiva_quandoFazLogin_entaoRecebeTokensEAbreSessaoDoAparelho() throws Exception {
        cadastrar(StatusConta.ATIVA);

        LoginResponse tokens = lerTokens(login(EMAIL, SENHA, "iPhone da Maria")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.expiraEm", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.papel", is("EMPREGADOR"))));

        Sessao sessao = sessaoDo(tokens);
        assertThat(sessao.getDispositivo()).isEqualTo("iPhone da Maria");
        assertThat(sessao.getRefreshTokenHash()).isNotEqualTo(tokens.refreshToken());
        assertThat(sessao.getRevogadaEm()).isNull();
    }

    @Test
    void dadoSenhaErrada_quandoFazLogin_entaoRecusaIgualAEmailInexistente() throws Exception {
        cadastrar(StatusConta.ATIVA);

        String senhaErrada = login(EMAIL, "senhaErrada99", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("CREDENCIAIS_INVALIDAS")))
                .andReturn().getResponse().getContentAsString();
        String emailInexistente = login("ninguem@example.com", SENHA, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("CREDENCIAIS_INVALIDAS")))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(senhaErrada).get("mensagem"))
                .isEqualTo(objectMapper.readTree(emailInexistente).get("mensagem"));
        assertThat(tentativaLoginRepository.count()).isEqualTo(2);
        assertThat(sessaoRepository.count()).isZero();
    }

    @Test
    void dadoCincoSenhasErradas_quandoTentaDeNovo_entaoBloqueiaMesmoComSenhaCerta() throws Exception {
        cadastrar(StatusConta.ATIVA);
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "senhaErrada99", null).andExpect(status().isUnauthorized());
        }

        login(EMAIL, SENHA, null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo", is("LOGIN_TEMPORARIAMENTE_BLOQUEADO")));

        assertThat(sessaoRepository.count()).isZero();
    }

    @Test
    void dadoContaSuspensa_quandoFazLoginComSenhaCerta_entaoRecusaComContaSuspensa() throws Exception {
        cadastrar(StatusConta.BLOQUEADA);

        login(EMAIL, SENHA, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("CONTA_SUSPENSA")));

        assertThat(sessaoRepository.count()).isZero();
    }

    @Test
    void dadoDoisAparelhosLogados_quandoUmFazLogout_entaoSoASessaoDeleERevogada() throws Exception {
        cadastrar(StatusConta.ATIVA);
        LoginResponse celular = logado("celular");
        LoginResponse tablet = logado("tablet");

        logout(celular).andExpect(status().isNoContent());

        assertThat(sessaoDo(celular).getRevogadaEm()).isNotNull();
        assertThat(sessaoDo(tablet).getRevogadaEm()).isNull();
        renovar(tablet.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void dadoSessaoRevogada_quandoRenova_entaoRecusaCom401() throws Exception {
        cadastrar(StatusConta.ATIVA);
        LoginResponse celular = logado("celular");
        logout(celular).andExpect(status().isNoContent());

        renovar(celular.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("SESSAO_INVALIDA")));
    }

    @Test
    void dadoSessaoAtiva_quandoRenova_entaoRefreshTokenAnteriorDeixaDeValer() throws Exception {
        cadastrar(StatusConta.ATIVA);
        LoginResponse login = logado("celular");

        LoginResponse renovado = lerTokens(renovar(login.refreshToken()).andExpect(status().isOk()));

        assertThat(renovado.refreshToken()).isNotEqualTo(login.refreshToken());
        renovar(login.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("SESSAO_INVALIDA")));
        renovar(renovado.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void dadoUsuarioLogado_quandoTrocaParaPrestador_entaoPapelValeProximoLoginERenovacao() throws Exception {
        cadastrar(StatusConta.ATIVA);
        LoginResponse celular = logado("celular");

        trocarPapel(celular.token(), "PRESTADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papelAtivo", is("PRESTADOR")));

        assertThat(usuarioRepository.findAll().get(0).getPapelAtivo()).isEqualTo(PapelUsuario.PRESTADOR);
        assertThat(logado("tablet").papel()).isEqualTo(PapelUsuario.PRESTADOR);
        renovar(celular.refreshToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel", is("PRESTADOR")));
    }

    @Test
    void dadoPapelForaDeEmpregadorEPrestador_quandoTroca_entaoRecusaSemAlterar() throws Exception {
        cadastrar(StatusConta.ATIVA);
        LoginResponse celular = logado("celular");

        trocarPapel(celular.token(), "ADMIN")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("PAPEL_INVALIDO")));
        trocarPapel(celular.token(), "SUPERVISOR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        assertThat(usuarioRepository.findAll().get(0).getPapelAtivo()).isEqualTo(PapelUsuario.EMPREGADOR);
    }

    @Test
    void dadoEmailMaiorQueAColuna_quandoFazLogin_entaoRecusaCom400() throws Exception {
        // formato válido pro @Email (até 320 caracteres), mas maior que a coluna de 255
        String local = "a".repeat(64);
        String dominio = "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + "." + "e".repeat(40) + ".com";

        login(local + "@" + dominio, SENHA, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        assertThat(tentativaLoginRepository.count()).isZero();
    }

    @Test
    void dadoDispositivoComCaracteresDeControle_quandoFazLogin_entaoGravaSemEles() throws Exception {
        cadastrar(StatusConta.ATIVA);

        LoginResponse tokens = lerTokens(login(EMAIL, SENHA, "iPhone\u0000 da\r\n Maria\t").andExpect(status().isOk()));

        assertThat(sessaoDo(tokens).getDispositivo()).isEqualTo("iPhone da Maria");
    }

    @Test
    void dadoCorpoComContentTypeErrado_quandoFazLogin_entaoRecusaCom415() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"email\":\"maria@example.com\",\"senha\":\"senhaForte123\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo", is("REQUISICAO_INVALIDA")));
    }

    @Test
    void dadoContaComEmailGravadoEmOutraGrafia_quandoErraSenha_entaoContaPeloEmailDaConta() throws Exception {
        Usuario usuario = cadastrar(StatusConta.ATIVA);
        usuario.setEmail("Maria@Example.com");
        usuarioRepository.save(usuario);
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "senhaErrada99", null).andExpect(status().isUnauthorized());
        }

        assertThat(tentativaLoginRepository.findAll())
                .allSatisfy(falha -> assertThat(falha.getEmail()).isEqualTo("Maria@Example.com"));
        login(EMAIL, SENHA, null).andExpect(status().isTooManyRequests());
    }

    @Test
    void dadoRajadaParalelaDeSenhasErradas_quandoChegaJunta_entaoSoCincoSaoConferidas() throws Exception {
        cadastrar(StatusConta.ATIVA);
        int paralelas = 20;
        ExecutorService pool = Executors.newFixedThreadPool(paralelas);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> status = new ArrayList<>();
        try {
            for (int i = 0; i < paralelas; i++) {
                status.add(pool.submit(() -> {
                    largada.await();
                    return login(EMAIL, "senhaErrada99", null).andReturn().getResponse().getStatus();
                }));
            }
            largada.countDown();
            List<Integer> respostas = new ArrayList<>();
            for (Future<Integer> resposta : status) {
                respostas.add(resposta.get(60, TimeUnit.SECONDS));
            }

            assertThat(respostas).filteredOn(s -> s == 401).hasSize(5);
            assertThat(respostas).filteredOn(s -> s == 429).hasSize(paralelas - 5);
        } finally {
            pool.shutdownNow();
        }
        assertThat(tentativaLoginRepository.count()).isEqualTo(5);
        login(EMAIL, SENHA, null).andExpect(status().isTooManyRequests());
    }

    @Test
    void dadoRefreshTokenDeOutraPessoa_quandoFazLogout_entaoNaoRevogaASessaoDela() throws Exception {
        cadastrar(StatusConta.ATIVA);
        cadastrar(OUTRO_EMAIL, OUTRO_CPF, StatusConta.ATIVA);
        LoginResponse maria = logado("celular");
        LoginResponse joao = logadoComo(OUTRO_EMAIL, "notebook");

        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + joao.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(maria.refreshToken()))))
                .andExpect(status().isNoContent());

        assertThat(sessaoDo(maria).getRevogadaEm()).isNull();
        renovar(maria.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void dadoCamposExtrasNoCorpo_quandoTrocaPapel_entaoSoMudaOPapelDoProprioUsuario() throws Exception {
        cadastrar(StatusConta.ATIVA);
        Usuario joao = cadastrar(OUTRO_EMAIL, OUTRO_CPF, StatusConta.ATIVA);
        LoginResponse maria = logado("celular");

        mockMvc.perform(patch("/auth/papel-ativo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + maria.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "papel", "PRESTADOR",
                                "usuarioId", joao.getId().toString(),
                                "id", joao.getId().toString(),
                                "papelAtivo", "ADMIN",
                                "statusConta", "BLOQUEADA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papelAtivo", is("PRESTADOR")));

        Usuario mariaGravada = usuarioRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(mariaGravada.getPapelAtivo()).isEqualTo(PapelUsuario.PRESTADOR);
        assertThat(mariaGravada.getStatusConta()).isEqualTo(StatusConta.ATIVA);
        Usuario joaoGravado = usuarioRepository.findById(joao.getId()).orElseThrow();
        assertThat(joaoGravado.getPapelAtivo()).isEqualTo(PapelUsuario.EMPREGADOR);
        assertThat(joaoGravado.getStatusConta()).isEqualTo(StatusConta.ATIVA);
    }

    @Test
    void dadoRenovacoesSimultaneasComOMesmoRefreshToken_quandoChegamJuntas_entaoSoUmaPassa() throws Exception {
        cadastrar(StatusConta.ATIVA);
        String refreshToken = logado("celular").refreshToken();
        int paralelas = 10;
        ExecutorService pool = Executors.newFixedThreadPool(paralelas);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> status = new ArrayList<>();
        try {
            for (int i = 0; i < paralelas; i++) {
                status.add(pool.submit(() -> {
                    largada.await();
                    return renovar(refreshToken).andReturn().getResponse().getStatus();
                }));
            }
            largada.countDown();
            List<Integer> respostas = new ArrayList<>();
            for (Future<Integer> resposta : status) {
                respostas.add(resposta.get(60, TimeUnit.SECONDS));
            }

            assertThat(respostas).filteredOn(s -> s == 200).hasSize(1);
            assertThat(respostas).filteredOn(s -> s == 401).hasSize(paralelas - 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void dadoTokenAusenteOuInvalido_quandoAcessaRotaAutenticada_entaoRecusaCom401() throws Exception {
        mockMvc.perform(patch("/auth/papel-ativo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"papel\":\"PRESTADOR\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));

        trocarPapel("token-invalido", "PRESTADOR")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }
}
