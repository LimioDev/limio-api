package com.limio.api.modulos.notificacao.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.NotificacaoRepository;
import com.limio.api.modulos.notificacao.enums.TipoNotificacao;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) da tela "Notificações" (ETI-110). Ninguém grava notificação ainda (o
 * envio é o ETI-112), então elas nascem direto pelo repository.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NotificacoesStoryTest {

    private static final String SENHA = "senhaForte123";
    private static final Instant BASE = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private Usuario maria;
    private Usuario rafael;

    @BeforeEach
    void prepararBase() {
        limparBase();
        maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        rafael = usuario("Rafael Souza", "rafael@example.com", "11144477735", PapelUsuario.PRESTADOR);
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private void limparBase() {
        jdbc.execute("delete from notificacao");
        jdbc.execute("delete from preferencia_notificacao");
        jdbc.execute("delete from cpf_bloqueado");
        jdbc.execute("TRUNCATE TABLE usuario CASCADE");
    }

    // ---------- listar ----------

    @Test
    void dadoNotificacoes_quandoLista_entaoSoAsMinhasDaMaisNovaPraMaisAntigaComOTotalDeNaoLidas() throws Exception {
        UUID anuncioId = UUID.randomUUID();
        Notificacao antiga = notificacao(maria, TipoNotificacao.ANUNCIO_CANCELADO, BASE.minus(Duration.ofDays(2)),
                BASE.minus(Duration.ofDays(1)), null);
        Notificacao nova = notificacao(maria, TipoNotificacao.NOVA_CANDIDATURA, BASE, null, anuncioId);
        notificacao(maria, TipoNotificacao.CHECKOUT_ENVIADO, BASE.minus(Duration.ofHours(1)), null, anuncioId);
        notificacao(rafael, TipoNotificacao.NOVO_SERVICO_NA_AREA, BASE.plusSeconds(10), null, anuncioId);

        listar(logar(maria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas", is(2)))
                .andExpect(jsonPath("$.notificacoes.totalElementos", is(3)))
                .andExpect(jsonPath("$.notificacoes.content", hasSize(3)))
                .andExpect(jsonPath("$.notificacoes.content[0].id", is(nova.getId().toString())))
                .andExpect(jsonPath("$.notificacoes.content[0].tipo", is("NOVA_CANDIDATURA")))
                .andExpect(jsonPath("$.notificacoes.content[0].titulo", is("Título NOVA_CANDIDATURA")))
                .andExpect(jsonPath("$.notificacoes.content[0].corpo", notNullValue()))
                .andExpect(jsonPath("$.notificacoes.content[0].enviadaEm", notNullValue()))
                .andExpect(jsonPath("$.notificacoes.content[0].lidaEm", nullValue()))
                .andExpect(jsonPath("$.notificacoes.content[0].anuncioId", is(anuncioId.toString())))
                .andExpect(jsonPath("$.notificacoes.content[2].id", is(antiga.getId().toString())))
                .andExpect(jsonPath("$.notificacoes.content[2].lidaEm", notNullValue()))
                .andExpect(jsonPath("$.notificacoes.content[2].anuncioId", nullValue()));
    }

    @Test
    void dadoMaisDeUmaPagina_quandoPedeASegunda_entaoVemORestoEONaoLidasDoTotal() throws Exception {
        for (int i = 0; i < 22; i++) {
            notificacao(maria, TipoNotificacao.NOVA_CANDIDATURA, BASE.minusSeconds(i), null, null);
        }

        mockMvc.perform(get("/notificacoes").param("pagina", "1").header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificacoes.content", hasSize(2)))
                .andExpect(jsonPath("$.notificacoes.pagina", is(1)))
                .andExpect(jsonPath("$.notificacoes.tamanho", is(20)))
                .andExpect(jsonPath("$.notificacoes.totalPaginas", is(2)))
                .andExpect(jsonPath("$.naoLidas", is(22)));
    }

    @Test
    void dadoNenhumaNotificacao_quandoLista_entaoListaVaziaEZeroNaoLidas() throws Exception {
        listar(logar(maria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificacoes.content", hasSize(0)))
                .andExpect(jsonPath("$.naoLidas", is(0)));
    }

    @Test
    void dadoPaginaNegativa_quandoLista_entao400() throws Exception {
        mockMvc.perform(get("/notificacoes").param("pagina", "-1").header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoSemToken_quandoLista_entao401() throws Exception {
        mockMvc.perform(get("/notificacoes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }

    // ---------- marcar uma ----------

    @Test
    void dadoNaoLida_quandoAbre_entaoFicaLidaEOSinoDiminui() throws Exception {
        Notificacao aberta = notificacao(maria, TipoNotificacao.NOVA_CANDIDATURA, BASE, null, null);
        notificacao(maria, TipoNotificacao.CHECKOUT_ENVIADO, BASE.minusSeconds(5), null, null);

        marcarComoLida(logar(maria), aberta.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas", is(1)));

        assertThat(lidaEm(aberta)).isNotNull();
    }

    @Test
    void dadoJaLida_quandoAbreDeNovo_entao200SemMudarADataDaPrimeiraLeitura() throws Exception {
        Instant primeira = BASE.minus(Duration.ofHours(3));
        Notificacao lida = notificacao(maria, TipoNotificacao.NOVA_CANDIDATURA, BASE.minus(Duration.ofDays(1)),
                primeira, null);

        marcarComoLida(logar(maria), lida.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas", is(0)));

        assertThat(lidaEm(lida)).isEqualTo(primeira);
    }

    @Test
    void dadoNotificacaoDeOutraPessoa_quandoTentaMarcar_entao404ENaoMexeNela() throws Exception {
        Notificacao doRafael = notificacao(rafael, TipoNotificacao.NOVO_SERVICO_NA_AREA, BASE, null, null);

        marcarComoLida(logar(maria), doRafael.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("NOTIFICACAO_NAO_ENCONTRADA")));

        assertThat(lidaEm(doRafael)).isNull();
    }

    @Test
    void dadoIdInexistente_quandoMarca_entao404() throws Exception {
        marcarComoLida(logar(maria), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("NOTIFICACAO_NAO_ENCONTRADA")));
    }

    // ---------- marcar todas ----------

    @Test
    void dadoVariasNaoLidas_quandoMarcaTodas_entaoSoAsMinhasFicamLidasEAsJaLidasMantemAData() throws Exception {
        Instant primeira = BASE.minus(Duration.ofHours(3));
        Notificacao jaLida = notificacao(maria, TipoNotificacao.ANUNCIO_CANCELADO, BASE.minus(Duration.ofDays(1)),
                primeira, null);
        Notificacao naoLida1 = notificacao(maria, TipoNotificacao.NOVA_CANDIDATURA, BASE, null, null);
        Notificacao naoLida2 = notificacao(maria, TipoNotificacao.CHECKOUT_ENVIADO, BASE.minusSeconds(5), null, null);
        Notificacao doRafael = notificacao(rafael, TipoNotificacao.NOVO_SERVICO_NA_AREA, BASE, null, null);

        mockMvc.perform(post("/notificacoes/lidas").header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas", is(0)));

        assertThat(lidaEm(naoLida1)).isNotNull();
        assertThat(lidaEm(naoLida2)).isNotNull();
        assertThat(lidaEm(jaLida)).isEqualTo(primeira);
        assertThat(lidaEm(doRafael)).as("a do Rafael não é da Maria").isNull();
    }

    @Test
    void dadoNadaPraMarcar_quandoMarcaTodas_entao200() throws Exception {
        mockMvc.perform(post("/notificacoes/lidas").header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas", is(0)));
    }

    // ---------- conta encerrada ----------

    @Test
    void dadoNotificacoes_quandoEncerraAConta_entaoAsDelaSaoApagadasEAsDosOutrosFicam() throws Exception {
        notificacao(maria, TipoNotificacao.NOVA_CANDIDATURA, BASE, null, null);
        notificacao(rafael, TipoNotificacao.NOVO_SERVICO_NA_AREA, BASE, null, null);

        mockMvc.perform(post("/auth/encerrar-conta")
                        .header(HttpHeaders.AUTHORIZATION, bearer(maria))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confirmacao", true))))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from notificacao where usuario_id = ?", Integer.class,
                maria.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notificacao where usuario_id = ?", Integer.class,
                rafael.getId())).isEqualTo(1);
    }

    // ---------- apoio ----------

    private Usuario usuario(String nome, String email, String cpf, PapelUsuario papel) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nome)
                .email(email)
                .telefone("11999998888")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(30))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(papel)
                .statusConta(StatusConta.ATIVA)
                .emailConfirmadoEm(Instant.now())
                .build());
    }

    private Notificacao notificacao(Usuario dono, TipoNotificacao tipo, Instant enviadaEm, Instant lidaEm,
            UUID anuncioId) {
        return notificacaoRepository.save(Notificacao.builder()
                .usuarioId(dono.getId())
                .anuncioId(anuncioId)
                .tipo(tipo)
                .titulo("Título " + tipo)
                .corpo("Corpo de " + tipo)
                .enviadaEm(enviadaEm)
                .lidaEm(lidaEm)
                .build());
    }

    private Instant lidaEm(Notificacao notificacao) {
        return jdbc.queryForObject("select lida_em from notificacao where id = ?", Instant.class, notificacao.getId());
    }

    private String logar(Usuario usuario) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(usuario.getEmail(), SENHA, "celular"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    private String bearer(Usuario usuario) throws Exception {
        return "Bearer " + logar(usuario);
    }

    private ResultActions listar(String token) throws Exception {
        return mockMvc.perform(get("/notificacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions marcarComoLida(String token, UUID notificacaoId) throws Exception {
        return mockMvc.perform(post("/notificacoes/{id}/lida", notificacaoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
