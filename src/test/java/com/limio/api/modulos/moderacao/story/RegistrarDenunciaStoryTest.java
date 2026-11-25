package com.limio.api.modulos.moderacao.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.ImagensDeTeste;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
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

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta do ETI-114: rota multipart -> usecase -> portas de {@code auth}, {@code anuncio} e {@code chat} ->
 * banco real. O storage é mock: a story confere a chave enviada e a {@code Midia} gravada, não o bucket.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RegistrarDenunciaStoryTest {

    private static final String SENHA = "senhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private ArmazenamentoService armazenamentoService;

    private Usuario maria;
    private Usuario carlos;
    private Usuario joana;

    @BeforeEach
    void prepararBase() {
        limparBase();
        maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        carlos = usuario("Carlos Souza", "carlos@example.com", "11144477735", PapelUsuario.PRESTADOR);
        joana = usuario("Joana Lima", "joana@example.com", "39053344705", PapelUsuario.PRESTADOR);
    }

    @AfterEach
    void limparBase() {
        jdbc.execute("delete from denuncia");
        jdbc.execute("delete from midia");
        jdbc.execute("delete from mensagem");
        jdbc.execute("delete from chat");
        jdbc.execute("delete from aplicacao");
        jdbc.execute("delete from anuncio");
        jdbc.execute("delete from cpf_bloqueado");
        jdbc.execute("TRUNCATE TABLE usuario CASCADE");
    }

    @Test
    void denunciaUmPerfilERecebeONumero() throws Exception {
        denunciar(maria, Map.of("denunciadoId", carlos.getId(), "motivo", "COMPORTAMENTO_INADEQUADO",
                "descricao", "  Me tratou mal pelo chat.  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.numero", greaterThan(0)))
                .andExpect(jsonPath("$.statusResolucao", is("ABERTA")))
                .andExpect(jsonPath("$.prazoAnalise", notNullValue()));

        Map<String, Object> denuncia = jdbc.queryForMap("select * from denuncia");
        assertThat(denuncia.get("denunciante_id")).isEqualTo(maria.getId());
        assertThat(denuncia.get("denunciado_id")).isEqualTo(carlos.getId());
        assertThat(denuncia.get("anuncio_id")).isNull();
        assertThat(denuncia.get("motivo")).isEqualTo("COMPORTAMENTO_INADEQUADO");
        assertThat(denuncia.get("descricao")).isEqualTo("Me tratou mal pelo chat.");
        assertThat(denuncia.get("status_resolucao")).isEqualTo("ABERTA");
        assertThat(denuncia.get("evidencia_id")).isNull();
    }

    @Test
    void numerosSaoSequenciais() throws Exception {
        long primeiro = numero(denunciar(maria, perfil(carlos)));
        long segundo = numero(denunciar(joana, perfil(carlos)));

        assertThat(segundo).isEqualTo(primeiro + 1);
    }

    @Test
    void prestadorDenunciaNaoPagamentoDeServicoConcluidoComFotoSemMexerNoServico() throws Exception {
        Anuncio servico = servico(StatusAnuncio.CONCLUIDO);

        denunciar(carlos, Map.of("anuncioId", servico.getId(), "motivo", "NAO_PAGOU_VALOR_COMBINADO",
                "descricao", "Terminei e ela não pagou."), png())
                .andExpect(status().isCreated());

        Map<String, Object> denuncia = jdbc.queryForMap("select * from denuncia");
        assertThat(denuncia.get("denunciado_id")).isEqualTo(maria.getId());
        assertThat(denuncia.get("anuncio_id")).isEqualTo(servico.getId());
        Map<String, Object> midia = jdbc.queryForMap("select * from midia where id = ?", denuncia.get("evidencia_id"));
        assertThat(midia.get("tipo")).isEqualTo("EVIDENCIA_DENUNCIA");
        assertThat(midia.get("usuario_id")).isEqualTo(carlos.getId());
        assertThat(midia.get("anuncio_id")).isNull();
        verify(armazenamentoService).enviar(startsWith("evidencias-denuncia/" + carlos.getId() + "/"), any(),
                eq("image/png"));
        assertThat(statusDoAnuncio(servico)).isEqualTo("CONCLUIDO");
        assertThat(jdbc.queryForObject("select status from aplicacao where anuncio_id = ?", String.class,
                servico.getId())).isEqualTo("CONCLUIDA");
    }

    @Test
    void denunciaUmaMensagemDoChat() throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        UUID candidaturaId = candidatura(anuncio, StatusAplicacao.ENVIADA);
        UUID mensagemId = mensagem(chat(anuncio, candidaturaId), carlos, "Me passa seu PIX antes de eu ir.");

        denunciar(maria, Map.of("mensagemId", mensagemId, "motivo", "SUSPEITA_DE_FRAUDE",
                "descricao", "Pediu pagamento adiantado."))
                .andExpect(status().isCreated());

        Map<String, Object> denuncia = jdbc.queryForMap("select * from denuncia");
        assertThat(denuncia.get("denunciado_id")).isEqualTo(carlos.getId());
        assertThat(denuncia.get("mensagem_id")).isEqualTo(mensagemId);
        assertThat(denuncia.get("candidatura_id")).isEqualTo(candidaturaId);
        assertThat(denuncia.get("anuncio_id")).isEqualTo(anuncio.getId());
    }

    @Test
    void denunciaOChatPelaCandidatura() throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        UUID candidaturaId = candidatura(anuncio, StatusAplicacao.ENVIADA);

        denunciar(carlos, Map.of("candidaturaId", candidaturaId, "motivo", "OUTRO_MOTIVO",
                "descricao", "Mandou mensagens ofensivas."))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("select denunciado_id from denuncia", UUID.class)).isEqualTo(maria.getId());
    }

    @Test
    void semMotivoERecusada() throws Exception {
        denunciar(maria, Map.of("denunciadoId", carlos.getId(), "descricao", "Me tratou mal."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        denunciar(maria, Map.of("denunciadoId", carlos.getId(), "motivo", "MOTIVO_INEXISTENTE",
                "descricao", "Me tratou mal."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        assertThat(jdbc.queryForObject("select count(*) from denuncia", Long.class)).isZero();
    }

    @Test
    void semAlvoComDoisContextosOuSemDescricaoERecusada() throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        UUID candidaturaId = candidatura(anuncio, StatusAplicacao.ENVIADA);

        denunciar(maria, Map.of("motivo", "OUTRO_MOTIVO", "descricao", "Algo errado."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        denunciar(maria, Map.of("anuncioId", anuncio.getId(), "candidaturaId", candidaturaId, "motivo", "OUTRO_MOTIVO",
                "descricao", "Algo errado."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        denunciar(maria, Map.of("denunciadoId", carlos.getId(), "motivo", "OUTRO_MOTIVO", "descricao", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        denunciar(maria, Map.of("denunciadoId", carlos.getId(), "motivo", "OUTRO_MOTIVO",
                "descricao", "x".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        assertThat(jdbc.queryForObject("select count(*) from denuncia", Long.class)).isZero();
    }

    @Test
    void terceiroNaoDenunciaServicoDoQualNaoParticipou() throws Exception {
        Anuncio servico = servico(StatusAnuncio.EM_ANDAMENTO);

        denunciar(joana, Map.of("anuncioId", servico.getId(), "motivo", "SERVICO_NAO_REALIZADO",
                "descricao", "Não apareceu."), png())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ALVO_NAO_ACESSIVEL")));

        assertThat(jdbc.queryForObject("select count(*) from denuncia", Long.class)).isZero();
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
    }

    @Test
    void terceiroNaoDenunciaMensagemDeChatAlheio() throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        UUID mensagemId = mensagem(chat(anuncio, candidatura(anuncio, StatusAplicacao.ENVIADA)), carlos, "Oi");

        denunciar(joana, Map.of("mensagemId", mensagemId, "motivo", "OUTRO_MOTIVO", "descricao", "Spam."))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ALVO_NAO_ACESSIVEL")));
    }

    @Test
    void anuncioSemEscolhidoQualquerUmDenunciaMenosODono() throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);

        denunciar(joana, Map.of("anuncioId", anuncio.getId(), "motivo", "SUSPEITA_DE_FRAUDE",
                "descricao", "Anúncio pede pagamento adiantado."))
                .andExpect(status().isCreated());
        denunciar(maria, Map.of("anuncioId", anuncio.getId(), "motivo", "SUSPEITA_DE_FRAUDE",
                "descricao", "Teste."))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ALVO_NAO_ACESSIVEL")));

        assertThat(jdbc.queryForObject("select denunciado_id from denuncia", UUID.class)).isEqualTo(maria.getId());
    }

    @Test
    void anuncioCanceladoDepoisDaEscolhaSegueEntreAsPartes() throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.ENCERRADO);
        candidatura(anuncio, StatusAplicacao.ENCERRADA);

        denunciar(maria, Map.of("anuncioId", anuncio.getId(), "motivo", "SERVICO_NAO_REALIZADO",
                "descricao", "Sumiu depois de escolhido."))
                .andExpect(status().isCreated());
        denunciar(joana, Map.of("anuncioId", anuncio.getId(), "motivo", "SUSPEITA_DE_FRAUDE",
                "descricao", "Teste."))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ALVO_NAO_ACESSIVEL")));

        assertThat(jdbc.queryForObject("select denunciado_id from denuncia", UUID.class)).isEqualTo(carlos.getId());
    }

    @Test
    void anuncioEncerradoSemNuncaTerEscolhidoNaoEAcessivel() throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.ENCERRADO);

        denunciar(joana, Map.of("anuncioId", anuncio.getId(), "motivo", "SUSPEITA_DE_FRAUDE",
                "descricao", "Anúncio pedia pagamento adiantado."))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ALVO_NAO_ACESSIVEL")));
    }

    @Test
    void mesmaDenunciaMandadaDeNovoDevolveAPrimeira() throws Exception {
        long primeiro = numero(denunciar(maria, perfil(carlos)));
        long repetido = numero(denunciar(maria, perfil(carlos), png()));

        assertThat(repetido).isEqualTo(primeiro);
        assertThat(jdbc.queryForObject("select count(*) from denuncia", Long.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from midia", Long.class)).isZero();
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());

        denunciar(maria, Map.of("denunciadoId", carlos.getId(), "motivo", "SUSPEITA_DE_FRAUDE",
                "descricao", "Outro motivo."))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from denuncia", Long.class)).isEqualTo(2L);
    }

    @Test
    void naoDenunciaASiMesmoNemPerfilInexistente() throws Exception {
        denunciar(maria, perfil(maria))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ALVO_NAO_ACESSIVEL")));
        denunciar(maria, Map.of("denunciadoId", UUID.randomUUID(), "motivo", "OUTRO_MOTIVO", "descricao", "Teste."))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ALVO_NAO_ACESSIVEL")));
    }

    @Test
    void fotoInvalidaERecusada() throws Exception {
        MockMultipartFile gif = new MockMultipartFile("foto", "prova.gif", "image/gif", ImagensDeTeste.gif(20, 20));

        denunciar(maria, perfil(carlos), gif)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo", is("FOTO_INVALIDA")));

        assertThat(jdbc.queryForObject("select count(*) from denuncia", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from midia", Long.class)).isZero();
    }

    @Test
    void semLoginERecusada() throws Exception {
        mockMvc.perform(multipart("/denuncias").param("denunciadoId", carlos.getId().toString())
                .param("motivo", "OUTRO_MOTIVO").param("descricao", "Teste."))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions denunciar(Usuario usuario, Map<String, Object> campos, MockMultipartFile... fotos)
            throws Exception {
        MockMultipartHttpServletRequestBuilder requisicao = multipart("/denuncias");
        campos.forEach((nome, valor) -> requisicao.param(nome, valor.toString()));
        for (MockMultipartFile foto : fotos) {
            requisicao.file(foto);
        }
        return mockMvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, bearer(usuario)));
    }

    private Map<String, Object> perfil(Usuario denunciado) {
        return Map.of("denunciadoId", denunciado.getId(), "motivo", "COMPORTAMENTO_INADEQUADO",
                "descricao", "Me tratou mal.");
    }

    private long numero(ResultActions resposta) throws Exception {
        String corpo = resposta.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("numero").asLong();
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("foto", "prova.png", "image/png", ImagensDeTeste.png(20, 20));
    }

    private String statusDoAnuncio(Anuncio anuncio) {
        return jdbc.queryForObject("select status from anuncio where id = ?", String.class, anuncio.getId());
    }

    /** Anúncio de Maria com Carlos escolhido, no status informado. */
    private Anuncio servico(StatusAnuncio status) {
        Anuncio anuncio = anuncio(status);
        candidatura(anuncio, status == StatusAnuncio.CONCLUIDO ? StatusAplicacao.CONCLUIDA : StatusAplicacao.ESCOLHIDA);
        return anuncio;
    }

    /** Anúncio de Maria. */
    private Anuncio anuncio(StatusAnuncio status) {
        return anuncioRepository.save(Anuncio.builder()
                .titulo("Instalar 3 tomadas")
                .categoriaId(jdbc.queryForObject("select id from categoria where protegida", UUID.class))
                .contratanteId(maria.getId())
                .descricao("Trocar três tomadas da cozinha")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(3)))
                .status(status)
                .validadoEm(status == StatusAnuncio.CONCLUIDO ? Instant.now().minus(Duration.ofDays(1)) : null)
                .build());
    }

    /** Candidatura de Carlos no anúncio. */
    private UUID candidatura(Anuncio anuncio, StatusAplicacao status) {
        boolean escolhida = status != StatusAplicacao.ENVIADA && status != StatusAplicacao.RECUSADA;
        return aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(carlos.getId())
                .status(status)
                .dataAplicacao(Instant.now().minus(Duration.ofDays(2)))
                .escolhidaEm(escolhida ? Instant.now().minus(Duration.ofDays(1)) : null)
                .build()).getId();
    }

    private UUID chat(Anuncio anuncio, UUID candidaturaId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into chat (id, aplicacao_id, anuncio_id, contratante_id, prestador_id) values (?, ?, ?, ?, ?)",
                id, candidaturaId, anuncio.getId(), maria.getId(), carlos.getId());
        return id;
    }

    private UUID mensagem(UUID chatId, Usuario remetente, String texto) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into mensagem (id, chat_id, remetente_id, texto, texto_exibido, data_envio)
                values (?, ?, ?, ?, ?, now())
                """, id, chatId, remetente.getId(), texto, texto);
        return id;
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
