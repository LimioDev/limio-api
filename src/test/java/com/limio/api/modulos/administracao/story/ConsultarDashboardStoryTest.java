package com.limio.api.modulos.administracao.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.seguranca.TokensDeTeste;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.administracao.administrador.Administrador;
import com.limio.api.modulos.administracao.administrador.AdministradorRepository;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldRepository;
import com.limio.api.modulos.financeiro.plano.Plano;
import com.limio.api.modulos.financeiro.plano.PlanoRepository;
import com.limio.api.modulos.moderacao.denuncia.AlvoDenuncia;
import com.limio.api.modulos.moderacao.denuncia.Denuncia;
import com.limio.api.modulos.moderacao.denuncia.DenunciaRepository;
import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta do ETI-117: {@code GET /admin/dashboard} -> usecase -> portas de {@code auth}, {@code anuncio},
 * {@code financeiro} e {@code moderacao} -> banco real. O token do administrador sai de {@link TokensDeTeste}, porque a
 * API ainda não tem a rota de login do admin (US60).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConsultarDashboardStoryTest {

    private static final String SENHA = "senhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdministradorRepository administradorRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    @Autowired
    private PlanoRepository planoRepository;

    @Autowired
    private AssinaturaGoldRepository assinaturaGoldRepository;

    @Autowired
    private DenunciaRepository denunciaRepository;

    private Administrador admin;
    private Usuario maria;
    private Usuario carlos;

    @BeforeEach
    void prepararBase() {
        limparBase();
        admin = administradorRepository.save(
                Administrador.cadastrar("Admin Limio", "admin@limio.com.br", passwordEncoder.encode(SENHA)));
        maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        carlos = usuario("Carlos Souza", "carlos@example.com", "11144477735", PapelUsuario.PRESTADOR);
    }

    @AfterEach
    void limparBase() {
        jdbc.execute("delete from denuncia");
        jdbc.execute("delete from midia");
        jdbc.execute("delete from aplicacao");
        jdbc.execute("delete from anuncio");
        jdbc.execute("TRUNCATE TABLE assinatura_gold CASCADE");
        jdbc.execute("delete from cpf_bloqueado");
        jdbc.execute("TRUNCATE TABLE usuario CASCADE");
        jdbc.execute("delete from administrador");
    }

    @Test
    void adminConsultaEOsContadoresBatemComABase() throws Exception {
        Usuario joana = usuario("Joana Lima", "joana@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Usuario encerrada = usuario("Ana Costa", "ana@example.com", "86288366757", PapelUsuario.PRESTADOR);
        encerrada.encerrar(Instant.now());
        usuarioRepository.save(encerrada);

        Anuncio publicado = anuncio(StatusAnuncio.PUBLICADO);
        Anuncio emAndamento = anuncio(StatusAnuncio.EM_ANDAMENTO);
        candidatura(publicado, joana, StatusAplicacao.ENVIADA, null);
        candidatura(emAndamento, carlos, StatusAplicacao.ESCOLHIDA, Instant.now());
        // escolhida e depois encerrada (o Prestador desistiu): continua contando como escolha
        candidatura(emAndamento, joana, StatusAplicacao.ENCERRADA, Instant.now().minus(Duration.ofDays(1)));

        assinatura(carlos, true);
        assinatura(joana, false); // pendente de pagamento: não entra

        denuncia(maria, carlos);
        denuncia(carlos, maria);

        consultarComoAdmin()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarios", is(3)))
                .andExpect(jsonPath("$.anuncios", is(2)))
                .andExpect(jsonPath("$.candidaturas", is(3)))
                .andExpect(jsonPath("$.escolhas", is(2)))
                .andExpect(jsonPath("$.assinaturasGoldAtivas", is(1)))
                .andExpect(jsonPath("$.denunciasAbertas", is(2)))
                .andExpect(jsonPath("$.statusOperacional", is("OPERANDO_NORMALMENTE")))
                .andExpect(jsonPath("$.statusOperacionalDescricao", is("Operando normalmente")));
    }

    @Test
    void umaDenunciaAbertaApareceNoContador() throws Exception {
        consultarComoAdmin().andExpect(jsonPath("$.denunciasAbertas", is(0)));

        denuncia(maria, carlos);

        consultarComoAdmin().andExpect(jsonPath("$.denunciasAbertas", is(1)));
    }

    @Test
    void consultarNaoAlteraNada() throws Exception {
        denuncia(maria, carlos);

        consultarComoAdmin().andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select status_resolucao from denuncia", String.class)).isEqualTo("ABERTA");
    }

    @Test
    void usuarioComumRecebe403() throws Exception {
        mockMvc.perform(get("/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
    }

    @Test
    void semTokenRecebe401() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }

    @Test
    void tokenDeAdministradorRemovidoRecebe401() throws Exception {
        String token = bearerAdmin();
        administradorRepository.delete(admin);

        mockMvc.perform(get("/admin/dashboard").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }

    @Test
    void administradorNaoUsaAsRotasDoApp() throws Exception {
        mockMvc.perform(get("/notificacoes").header(HttpHeaders.AUTHORIZATION, bearerAdmin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
    }

    private ResultActions consultarComoAdmin() throws Exception {
        return mockMvc.perform(get("/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearerAdmin()));
    }

    private String bearerAdmin() {
        return "Bearer " + TokensDeTeste.administrador(jwtEncoder, admin.getId());
    }

    private Anuncio anuncio(StatusAnuncio status) {
        return anuncioRepository.save(Anuncio.builder()
                .titulo("Instalar 3 tomadas")
                .categoriaId(jdbc.queryForObject("select id from categoria where protegida", UUID.class))
                .contratanteId(maria.getId())
                .descricao("Trocar três tomadas da cozinha")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(3)))
                .status(status)
                .build());
    }

    private void candidatura(Anuncio anuncio, Usuario prestador, StatusAplicacao status, Instant escolhidaEm) {
        aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(prestador.getId())
                .status(status)
                .dataAplicacao(Instant.now().minus(Duration.ofDays(2)))
                .escolhidaEm(escolhidaEm)
                .encerradaEm(status == StatusAplicacao.ENCERRADA ? Instant.now() : null)
                .build());
    }

    private void assinatura(Usuario prestador, boolean ativa) {
        Plano gold = planoRepository.findByCodigoAndAtivoTrue(Plano.CODIGO_GOLD).orElseThrow();
        AssinaturaGold assinatura = AssinaturaGold.nova(prestador.getId(), gold.getId(), Instant.now(),
                Instant.now().plus(Duration.ofDays(30)));
        if (ativa) {
            assinatura.confirmarAtiva();
        }
        assinaturaGoldRepository.save(assinatura);
    }

    private void denuncia(Usuario denunciante, Usuario denunciado) {
        denunciaRepository.save(Denuncia.registrar(denunciante.getId(),
                new AlvoDenuncia(denunciado.getId(), null, null, null), MotivoDenuncia.COMPORTAMENTO_INADEQUADO,
                "Me tratou mal.", null, Instant.now().plus(Duration.ofHours(72))));
    }

    private Usuario usuario(String nome, String email, String cpf, PapelUsuario papel) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nome)
                .email(email)
                .telefone("11988887777")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(30))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(papel)
                .statusConta(StatusConta.ATIVA)
                .build());
    }

    private String bearer(Usuario usuario) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(usuario.getEmail(), SENHA, "celular"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readValue(corpo, LoginResponse.class).token();
    }
}
