package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.portas.ConsultaAvaliacoes;
import com.limio.api.comum.portas.ResumoAvaliacoes;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.sessao.SessaoRepository;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do UC "Empregador listar candidatos" (ETI-67). Anúncios, candidaturas,
 * perfis e chats nascem direto pelo repository/SQL. Como ainda não existe módulo de avaliações, a porta é mockada
 * onde o cenário precisa de avaliação.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ListarCandidatosStoryTest {

    private static final String SENHA = "senhaForte123";
    private static final String TELEFONE_PRESTADOR = "11977776666";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SessaoRepository sessaoRepository;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ConsultaAvaliacoes consultaAvaliacoes;

    @BeforeEach
    void limparBase() {
        jdbc.execute("delete from mensagem");
        jdbc.execute("delete from chat");
        jdbc.execute("delete from prestador_categoria");
        jdbc.execute("delete from perfil_prestador");
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
        sessaoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private Usuario usuario(String nome, String email, String cpf, PapelUsuario papel) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nome)
                .email(email)
                .telefone(papel == PapelUsuario.PRESTADOR ? TELEFONE_PRESTADOR : "11988887777")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(30))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(papel)
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

    private Anuncio anuncio(UUID contratanteId) {
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(contratanteId)
                .titulo("Instalar 3 tomadas")
                .categoriaId(UUID.randomUUID()) // sem FK pra categoria (ADR-0001 §1.6)
                .descricao("Instalar 3 tomadas novas na sala e trocar o disjuntor")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(5)))
                .bairroRegiaoSnapshot("Pinheiros")
                .status(StatusAnuncio.PUBLICADO)
                .build());
    }

    private Aplicacao aplicacao(UUID anuncioId, UUID prestadorId, StatusAplicacao status, Instant em) {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(prestadorId)
                .status(status)
                .dataAplicacao(em)
                .build();
        if (status == StatusAplicacao.ESCOLHIDA) {
            aplicacao.setEscolhidaEm(em);
        }
        return aplicacaoRepository.save(aplicacao);
    }

    private UUID chat(Aplicacao aplicacao, UUID contratanteId) {
        return jdbc.queryForObject("""
                insert into chat (aplicacao_id, anuncio_id, contratante_id, prestador_id)
                values (?, ?, ?, ?) returning id""", UUID.class, aplicacao.getId(), aplicacao.getAnuncioId(),
                contratanteId, aplicacao.getPrestadorId());
    }

    private void especialidadeQualquer(UUID prestadorId) {
        UUID perfilId = jdbc.queryForObject("""
                insert into perfil_prestador (usuario_id, raio_atendimento_km, municipio_base, uf_base,
                    latitude_base, longitude_base, completo)
                values (?, 10, 'Campinas', 'SP', -22.9, -47.06, true) returning id""", UUID.class, prestadorId);
        jdbc.update("insert into prestador_categoria (perfil_prestador_id, categoria_id) "
                + "select ?, id from categoria limit 1", perfilId);
    }

    private ResultActions listar(String token, UUID anuncioId) throws Exception {
        return mockMvc.perform(get("/anuncios/{anuncioId}/candidaturas", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void dadoAnuncioComCandidatos_quandoLista_entaoDevolveSoOsEnviadosDaMaisAntigaPraMaisNova() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Usuario ana = usuario("Ana Lima", "ana@example.com", "16899535009", PapelUsuario.PRESTADOR);
        Usuario bruno = usuario("Bruno Reis", "bruno@example.com", "11144477735", PapelUsuario.PRESTADOR);
        Usuario carla = usuario("Carla Dias", "carla@example.com", "12345678909", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncio(maria.getId());
        Instant agora = Instant.now();
        Aplicacao daAna = aplicacao(anuncio.getId(), ana.getId(), StatusAplicacao.ENVIADA, agora.minusSeconds(120));
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA, agora.minusSeconds(60));
        aplicacao(anuncio.getId(), bruno.getId(), StatusAplicacao.RECUSADA, agora.minusSeconds(90));
        aplicacao(anuncio.getId(), carla.getId(), StatusAplicacao.ENCERRADA, agora.minusSeconds(30));

        listar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatos", hasSize(2)))
                .andExpect(jsonPath("$.candidatos[0].candidaturaId", is(daAna.getId().toString())))
                .andExpect(jsonPath("$.candidatos[0].nome", is("Ana L.")))
                .andExpect(jsonPath("$.candidatos[1].candidaturaId", is(doRafael.getId().toString())))
                .andExpect(jsonPath("$.candidatos[1].nome", is("Rafael S.")));
    }

    @Test
    void dadoAnuncioSemCandidatos_quandoLista_entao200ComListaVazia() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Anuncio anuncio = anuncio(maria.getId());

        listar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatos", hasSize(0)));
    }

    @Test
    void dadoAnuncioDeOutroUsuario_quandoLista_entao404() throws Exception {
        usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario joao = usuario("João Pereira", "joao@example.com", "11144477735", PapelUsuario.EMPREGADOR);
        Anuncio anuncioDeJoao = anuncio(joao.getId());

        listar(logar("maria@example.com"), anuncioDeJoao.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoCandidatoSemAvaliacoes_quandoLista_entaoMediaAusenteEQuantidadeZero() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncio(maria.getId());
        aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA, Instant.now());

        listar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatos[0].qtdAvaliacoes", is(0)))
                .andExpect(jsonPath("$.candidatos[0].mediaAvaliacoes", nullValue()))
                .andExpect(jsonPath("$.candidatos[0].gold", is(false)));
    }

    @Test
    void dadoCandidatoComAvaliacoes_quandoLista_entaoDevolveQuantidadeEMedia() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncio(maria.getId());
        aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA, Instant.now());
        when(consultaAvaliacoes.resumir(anyCollection()))
                .thenReturn(Map.of(rafael.getId(), new ResumoAvaliacoes(12, new BigDecimal("4.6"))));

        listar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatos[0].qtdAvaliacoes", is(12)))
                .andExpect(jsonPath("$.candidatos[0].mediaAvaliacoes", is(4.6)));
    }

    @Test
    void dadoCandidatoComChat_quandoLista_entaoDevolveOChatId() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Usuario ana = usuario("Ana Lima", "ana@example.com", "16899535009", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncio(maria.getId());
        Instant agora = Instant.now();
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA, agora.minusSeconds(60));
        aplicacao(anuncio.getId(), ana.getId(), StatusAplicacao.ENVIADA, agora);
        UUID chatId = chat(doRafael, maria.getId());

        listar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatos[0].chatId", is(chatId.toString())))
                // chat que ainda não foi criado: campo ausente, nunca criado por uma consulta
                .andExpect(jsonPath("$.candidatos[1].chatId", nullValue()));
    }

    @Test
    void dadoCandidatoComPerfil_quandoLista_entaoDevolveEspecialidadesESemDadosSensiveis() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncio(maria.getId());
        aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA, Instant.now());
        especialidadeQualquer(rafael.getId());

        String corpo = listar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatos[0].especialidades", hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo)
                .doesNotContain(TELEFONE_PRESTADOR)
                .doesNotContain("rafael@example.com")
                .doesNotContain("Rafael Souza") // só o nome abreviado sai
                .doesNotContainIgnoringCase("telefone")
                .doesNotContainIgnoringCase("whatsapp")
                .doesNotContainIgnoringCase("email")
                .doesNotContainIgnoringCase("endereco")
                .doesNotContain("Campinas"); // município-base do prestador também não sai
    }

    @Test
    void dadoSemToken_quandoLista_entao401() throws Exception {
        mockMvc.perform(get("/anuncios/{anuncioId}/candidaturas", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }
}
