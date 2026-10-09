package com.limio.api.modulos.auth.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

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
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.actions.helper.TokenRecuperacaoHelper;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.sessao.Sessao;
import com.limio.api.modulos.auth.sessao.SessaoRepository;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContato;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoRepository;
import com.limio.api.modulos.auth.verificacaocontato.enums.CanalVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do TICKET-0033 — recuperar
 * senha esquecida (UC06) e alterar senha autenticada (UC07). Sobe Spring
 * context + Testcontainers Postgres.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RecuperarEAlterarSenhaStoryTest {

    private static final String EMAIL = "maria@example.com";
    private static final String SENHA = "senhaForte123";
    private static final String NOVA_SENHA = "senhaNova456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SessaoRepository sessaoRepository;

    @Autowired
    private VerificacaoContatoRepository verificacaoContatoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void limparBase() {
        verificacaoContatoRepository.deleteAll();
        sessaoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    private Usuario cadastrar() {
        return usuarioRepository.save(Usuario.builder()
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

    private String gerarTokenRecuperacao(Usuario usuario, Instant expiraEm) {
        String token = TokenRecuperacaoHelper.gerar();
        verificacaoContatoRepository.save(VerificacaoContato.builder()
                .usuario(usuario)
                .canal(CanalVerificacao.EMAIL)
                .finalidade(FinalidadeVerificacao.RECUPERACAO_SENHA)
                .tokenHash(TokenRecuperacaoHelper.hash(token))
                .expiraEm(expiraEm)
                .build());
        return token;
    }

    private ResultActions solicitarRecuperacao(String email) throws Exception {
        return mockMvc.perform(post("/auth/recuperar-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions redefinirSenha(String token, String novaSenha) throws Exception {
        return mockMvc.perform(post("/auth/redefinir-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token, "novaSenha", novaSenha))));
    }

    private LoginResponse logar(String senha) throws Exception {
        ResultActions resultado = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, senha, "celular"))));
        return objectMapper.readValue(resultado.andReturn().getResponse().getContentAsString(), LoginResponse.class);
    }

    private ResultActions alterarSenha(String tokenAcesso, String senhaAtual, String novaSenha, String refreshToken)
            throws Exception {
        return mockMvc.perform(patch("/auth/senha")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAcesso)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("senhaAtual", senhaAtual, "novaSenha", novaSenha, "refreshToken", refreshToken))));
    }

    @Test
    void dadoEmailExistente_quandoSolicitaRecuperacao_entaoGeraTokenEResponde200() throws Exception {
        Usuario usuario = cadastrar();

        solicitarRecuperacao(EMAIL).andExpect(status().isOk());

        assertThat(verificacaoContatoRepository.findAll()).hasSize(1);
        assertThat(verificacaoContatoRepository.findAll().get(0).getUsuario().getId()).isEqualTo(usuario.getId());
    }

    @Test
    void dadoEmailInexistente_quandoSolicitaRecuperacao_entaoRespondeIgualAoEmailExistenteSemGerarToken()
            throws Exception {
        cadastrar();

        solicitarRecuperacao("ninguem@example.com").andExpect(status().isOk());

        assertThat(verificacaoContatoRepository.findAll()).isEmpty();
    }

    @Test
    void dadoQuatroSolicitacoesNaMesmaHora_entaoSoAsTresPrimeirasGeramToken() throws Exception {
        cadastrar();

        for (int i = 0; i < 4; i++) {
            solicitarRecuperacao(EMAIL).andExpect(status().isOk());
        }

        assertThat(verificacaoContatoRepository.findAll()).hasSize(3);
    }

    @Test
    void dadoTokenValido_quandoRedefineSenha_entaoTrocaSenhaConsomeTokenEEncerraSessoes() throws Exception {
        Usuario usuario = cadastrar();
        LoginResponse sessaoAntiga = logar(SENHA);
        String token = gerarTokenRecuperacao(usuario, Instant.now().plusSeconds(3600));

        redefinirSenha(token, NOVA_SENHA).andExpect(status().isOk());

        assertThat(verificacaoContatoRepository.findAll().get(0).getConsumidoEm()).isNotNull();
        Usuario atualizado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(NOVA_SENHA, atualizado.getSenhaHash())).isTrue();
        assertThat(sessaoRepository.findAll())
                .allSatisfy(sessao -> assertThat(sessao.getRevogadaEm()).isNotNull());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", sessaoAntiga.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dadoTokenExpirado_quandoRedefineSenha_entaoRecusaComTokenInvalido() throws Exception {
        Usuario usuario = cadastrar();
        String token = gerarTokenRecuperacao(usuario, Instant.now().minusSeconds(1));

        redefinirSenha(token, NOVA_SENHA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("TOKEN_RECUPERACAO_INVALIDO")));
    }

    @Test
    void dadoTokenJaUsado_quandoRedefineDeNovo_entaoRecusaComTokenInvalido() throws Exception {
        Usuario usuario = cadastrar();
        String token = gerarTokenRecuperacao(usuario, Instant.now().plusSeconds(3600));
        redefinirSenha(token, NOVA_SENHA).andExpect(status().isOk());

        redefinirSenha(token, "outraSenha789")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("TOKEN_RECUPERACAO_INVALIDO")));
    }

    @Test
    void dadoTokenInexistente_quandoRedefineSenha_entaoRecusaComTokenInvalido() throws Exception {
        redefinirSenha("token-que-nunca-existiu", NOVA_SENHA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("TOKEN_RECUPERACAO_INVALIDO")));
    }

    @Test
    void dadoNovaSenhaIgualAAtual_quandoRedefinePeloLink_entaoRecusa() throws Exception {
        Usuario usuario = cadastrar();
        String token = gerarTokenRecuperacao(usuario, Instant.now().plusSeconds(3600));

        redefinirSenha(token, SENHA)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("SENHA_IGUAL_ANTERIOR")));
    }

    @Test
    void dadoUsuarioLogadoEmDoisAparelhos_quandoAlteraSenha_entaoMantemOAparelhoAtualEEncerraOOutro()
            throws Exception {
        cadastrar();
        LoginResponse celular = logar(SENHA);
        LoginResponse tablet = logar(SENHA);

        alterarSenha(celular.token(), SENHA, NOVA_SENHA, celular.refreshToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken", is(org.hamcrest.Matchers.not(celular.refreshToken()))));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", tablet.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("SESSAO_INVALIDA")));

        Usuario atualizado = usuarioRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(passwordEncoder.matches(NOVA_SENHA, atualizado.getSenhaHash())).isTrue();
    }

    @Test
    void dadoSenhaAtualIncorreta_quandoAlteraSenha_entaoRecusa() throws Exception {
        cadastrar();
        LoginResponse celular = logar(SENHA);

        alterarSenha(celular.token(), "senhaErrada99", NOVA_SENHA, celular.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("SENHA_ATUAL_INCORRETA")));
    }

    @Test
    void dadoNovaSenhaIgualAAtual_quandoAlteraSenhaLogado_entaoRecusa() throws Exception {
        cadastrar();
        LoginResponse celular = logar(SENHA);

        alterarSenha(celular.token(), SENHA, SENHA, celular.refreshToken())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("SENHA_IGUAL_ANTERIOR")));
    }

    @Test
    void dadoSemTokenDeAcesso_quandoAlteraSenha_entaoRecusaComNaoAutenticado() throws Exception {
        cadastrar();

        mockMvc.perform(patch("/auth/senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("senhaAtual", SENHA, "novaSenha", NOVA_SENHA, "refreshToken", "qualquer"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }
}
