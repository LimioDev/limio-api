package com.limio.api.modulos.notificacao.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.auth.actions.service.EmailService;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.notificacao.actions.service.EmailAvisoService;
import com.limio.api.modulos.notificacao.actions.service.PushExpoService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta do envio dos avisos (ETI-112). O gatilho é real: o Prestador se candidata pela API ({@code POST
 * /anuncios/{id}/candidaturas}), o {@code anuncio} publica {@code CandidaturaCriadaEvent} e o Empregador recebe o
 * aviso "Novo candidato". Os provedores (Expo e Resend) são mocks; o envio roda na mesma thread
 * ({@code notificacao.envio.assincrono=false} nos testes).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EnvioDeAvisosStoryTest {

    private static final String SENHA = "senhaForte123";
    private static final String TOKEN_MARIA = "ExponentPushToken[maria-celular]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private PushExpoService pushExpoService;

    @MockitoBean
    private EmailAvisoService emailAvisoService;

    @MockitoBean
    private EmailService emailDeSegurancaService;

    private Usuario maria;
    private Usuario rafael;

    @BeforeEach
    void prepararBase() {
        limparBase();
        maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        rafael = usuario("Rafael Souza", "rafael@example.com", "11144477735", PapelUsuario.PRESTADOR);
        when(pushExpoService.enviar(anyList(), anyString(), anyString(), anyMap())).thenReturn(List.of());
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private void limparBase() {
        jdbc.execute("delete from envio_notificacao");
        jdbc.execute("delete from notificacao");
        jdbc.execute("delete from aparelho_push");
        jdbc.execute("delete from preferencia_notificacao");
        jdbc.execute("delete from mensagem");
        jdbc.execute("delete from chat");
        jdbc.execute("delete from aplicacao");
        jdbc.execute("delete from anuncio");
        jdbc.execute("delete from cpf_bloqueado");
        jdbc.execute("TRUNCATE TABLE usuario CASCADE");
    }

    // ---------- aviso na lista e canais ----------

    @Test
    void dadoPushLigadoEAparelhoRegistrado_quandoRecebeCandidato_entaoAvisoNaListaEPushNoAparelho() throws Exception {
        String tokenMaria = logar(maria);
        registrarAparelho(tokenMaria, TOKEN_MARIA, "ANDROID").andExpect(status().isNoContent());

        candidatarSe();

        mockMvc.perform(get("/notificacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenMaria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas", is(1)))
                .andExpect(jsonPath("$.notificacoes.content[0].tipo", is("NOVA_CANDIDATURA")))
                .andExpect(jsonPath("$.notificacoes.content[0].titulo", is("Novo candidato")))
                .andExpect(jsonPath("$.notificacoes.content[0].corpo",
                        is("Rafael S. se candidatou a 'Troca de chuveiro'.")));
        verify(pushExpoService).enviar(eq(List.of(TOKEN_MARIA)), eq("Novo candidato"),
                eq("Rafael S. se candidatou a 'Troca de chuveiro'."), anyMap());
        assertThat(envios()).containsExactly("PUSH:ENVIADO");
    }

    @Test
    void dadoCanaisDesligados_quandoRecebeCandidato_entaoSoEntraNaListaSemEnviar() throws Exception {
        preferencia(maria, false, false, false);
        registrarAparelho(logar(maria), TOKEN_MARIA, "IOS").andExpect(status().isNoContent());

        candidatarSe();

        assertThat(notificacoesDa(maria)).isEqualTo(1);
        verify(pushExpoService, never()).enviar(anyList(), anyString(), anyString(), anyMap());
        verify(emailAvisoService, never()).enviar(anyString(), anyString(), anyString());
        assertThat(envios()).isEmpty();
    }

    @Test
    void dadoEmailLigado_quandoRecebeCandidato_entaoEmailProEnderecoConfirmado() throws Exception {
        preferencia(maria, false, true, false);

        candidatarSe();

        verify(emailAvisoService).enviar(eq("maria@example.com"), eq("Novo candidato"), anyString());
        assertThat(envios()).containsExactly("EMAIL:ENVIADO");
    }

    /** Decisão do PO: sem AceiteTermo (ainda não existe), não há consentimento ativo e nada sai por WhatsApp. */
    @Test
    void dadoWhatsappLigadoSemConsentimento_quandoRecebeCandidato_entaoNadaSaiPorWhatsapp() throws Exception {
        preferencia(maria, false, false, true);

        candidatarSe();

        assertThat(notificacoesDa(maria)).isEqualTo(1);
        assertThat(envios()).isEmpty();
    }

    @Test
    void dadoFalhaDoProvedorDePush_quandoRecebeCandidato_entaoACandidaturaValeOAvisoFicaNaListaEOEmailSai()
            throws Exception {
        preferencia(maria, true, true, false);
        registrarAparelho(logar(maria), TOKEN_MARIA, "ANDROID").andExpect(status().isNoContent());
        when(pushExpoService.enviar(anyList(), anyString(), anyString(), anyMap()))
                .thenThrow(new IllegalStateException("Expo fora do ar"));

        candidatarSe();

        assertThat(jdbc.queryForObject("select status from aplicacao", String.class)).isEqualTo("ENVIADA");
        assertThat(notificacoesDa(maria)).isEqualTo(1);
        assertThat(envios()).containsExactlyInAnyOrder("PUSH:FALHOU", "EMAIL:ENVIADO");
        assertThat(jdbc.queryForObject("select detalhe from envio_notificacao where canal = 'PUSH'", String.class))
                .contains("Expo fora do ar");
    }

    @Test
    void dadoTokenQueOExpoDizNaoExistirMais_quandoEnvia_entaoOAparelhoEApagado() throws Exception {
        registrarAparelho(logar(maria), TOKEN_MARIA, "ANDROID").andExpect(status().isNoContent());
        when(pushExpoService.enviar(anyList(), anyString(), anyString(), anyMap())).thenReturn(List.of(TOKEN_MARIA));

        candidatarSe();

        assertThat(aparelhos()).isZero();
    }

    // ---------- e-mail de segurança ----------

    /** Decisão do PO: e-mail de segurança sai sempre, mesmo com o e-mail desligado nas preferências. */
    @Test
    void dadoEmailDesligado_quandoTrocaASenha_entaoOEmailDeSegurancaSaiMesmoAssim() throws Exception {
        preferencia(maria, false, false, false);
        JsonNode sessao = sessao(maria);

        mockMvc.perform(patch("/auth/senha")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessao.get("token").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("senhaAtual", SENHA,
                                "novaSenha", "outraSenha456", "refreshToken",
                                sessao.get("refreshToken").asString()))))
                .andExpect(status().isOk());

        verify(emailDeSegurancaService).enviarSenhaAlterada("maria@example.com");
    }

    // ---------- aparelho ----------

    @Test
    void dadoAparelhoRegistradoERemovidoNoLogout_quandoRecebeCandidato_entaoNaoHaPush() throws Exception {
        String tokenMaria = logar(maria);
        registrarAparelho(tokenMaria, TOKEN_MARIA, "ANDROID").andExpect(status().isNoContent());
        assertThat(aparelhos()).isEqualTo(1);

        mockMvc.perform(post("/notificacoes/aparelhos/remover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenMaria)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", TOKEN_MARIA))))
                .andExpect(status().isNoContent());
        assertThat(aparelhos()).isZero();

        candidatarSe();

        assertThat(notificacoesDa(maria)).isEqualTo(1);
        verify(pushExpoService, never()).enviar(anyList(), anyString(), anyString(), anyMap());
    }

    @Test
    void dadoMesmoAparelhoRegistradoDuasVezes_entaoUmRegistroSo() throws Exception {
        String tokenMaria = logar(maria);
        registrarAparelho(tokenMaria, TOKEN_MARIA, "ANDROID").andExpect(status().isNoContent());
        registrarAparelho(tokenMaria, TOKEN_MARIA, "ANDROID").andExpect(status().isNoContent());

        assertThat(aparelhos()).isEqualTo(1);
    }

    @Test
    void dadoOutraContaEntraNoMesmoAparelho_entaoOPushPassaPraEla() throws Exception {
        registrarAparelho(logar(maria), TOKEN_MARIA, "ANDROID").andExpect(status().isNoContent());
        registrarAparelho(logar(rafael), TOKEN_MARIA, "ANDROID").andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select usuario_id from aparelho_push", UUID.class)).isEqualTo(rafael.getId());
    }

    @Test
    void dadoTokenDeOutraConta_quandoTentaRemover_entao204SemApagar() throws Exception {
        registrarAparelho(logar(maria), TOKEN_MARIA, "ANDROID").andExpect(status().isNoContent());

        mockMvc.perform(post("/notificacoes/aparelhos/remover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar(rafael))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", TOKEN_MARIA))))
                .andExpect(status().isNoContent());

        assertThat(aparelhos()).isEqualTo(1);
    }

    @Test
    void dadoTokenForaDoFormatoDoExpo_quandoRegistra_entao400() throws Exception {
        registrarAparelho(logar(maria), "qualquer-coisa", "ANDROID")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoPlataformaDesconhecida_quandoRegistra_entao400() throws Exception {
        registrarAparelho(logar(maria), TOKEN_MARIA, "WINDOWS")
                .andExpect(status().isBadRequest());
    }

    // ---------- apoio ----------

    private void candidatarSe() throws Exception {
        Anuncio anuncio = anuncioRepository.save(Anuncio.builder()
                .contratanteId(maria.getId())
                .titulo("Troca de chuveiro")
                .categoriaId(UUID.randomUUID())
                .descricao("Trocar o chuveiro do banheiro")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(3)))
                .bairroRegiaoSnapshot("Perdizes")
                .status(StatusAnuncio.PUBLICADO)
                .build());
        mockMvc.perform(post("/anuncios/{id}/candidaturas", anuncio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar(rafael)))
                .andExpect(status().isCreated());
    }

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

    /** Direto no banco: a rota recusa whatsapp=true sem consentimento, e aqui o teste é justamente esse estado. */
    private void preferencia(Usuario usuario, boolean push, boolean email, boolean whatsapp) {
        jdbc.update("insert into preferencia_notificacao (usuario_id, push, email, whatsapp) values (?, ?, ?, ?)",
                usuario.getId(), push, email, whatsapp);
    }

    private ResultActions registrarAparelho(String token, String tokenPush, String plataforma) throws Exception {
        return mockMvc.perform(post("/notificacoes/aparelhos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", tokenPush, "plataforma", plataforma))));
    }

    private JsonNode sessao(Usuario usuario) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", usuario.getEmail(), "senha", SENHA,
                                "dispositivo", "celular"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo);
    }

    private String logar(Usuario usuario) throws Exception {
        return sessao(usuario).get("token").asString();
    }

    private int notificacoesDa(Usuario usuario) {
        return jdbc.queryForObject("select count(*) from notificacao where usuario_id = ?", Integer.class,
                usuario.getId());
    }

    private int aparelhos() {
        return jdbc.queryForObject("select count(*) from aparelho_push", Integer.class);
    }

    private List<String> envios() {
        return jdbc.queryForList("select canal || ':' || status from envio_notificacao", String.class);
    }
}
