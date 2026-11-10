package com.limio.api.modulos.moderacao.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
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
 * BDD ponta a ponta do ETI-107: rota -> usecase -> porta de {@code anuncio} -> banco real, e o evento levando a média
 * ao perfil de {@code perfil}. Ainda não há rota que conclua o serviço (check-out e validação são de outros cards): o
 * anúncio e a candidatura escolhida nascem pelo repository, já {@code CONCLUIDO}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AvaliarAOutraParteStoryTest {

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

    private Usuario maria;
    private Usuario carlos;

    @BeforeEach
    void prepararBase() {
        limparBase();
        maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        carlos = usuario("Carlos Souza", "carlos@example.com", "11144477735", PapelUsuario.PRESTADOR);
        perfilDeAtuacao(carlos);
    }

    @AfterEach
    void limparBase() {
        jdbc.execute("delete from avaliacao");
        jdbc.execute("delete from aplicacao");
        jdbc.execute("delete from anuncio");
        jdbc.execute("delete from perfil_prestador");
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from cpf_bloqueado");
        jdbc.execute("TRUNCATE TABLE usuario CASCADE");
    }

    @Test
    void empregadorAvaliaSoComNotaEOPrestadorGanhaAPrimeiraMedia() throws Exception {
        Anuncio servico = servicoConcluidoHa(Duration.ofDays(1));
        assertThat(mediaDoPrestador(carlos)).containsEntry("qtd_avaliacoes", 0).containsEntry("media_avaliacoes", null);

        avaliar(maria, servico, Map.of("nota", 4))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.anuncioId", is(servico.getId().toString())))
                .andExpect(jsonPath("$.nota", is(4)))
                .andExpect(jsonPath("$.comentario", nullValue()))
                .andExpect(jsonPath("$.publicadaEm", notNullValue()));

        Map<String, Object> avaliacao = jdbc.queryForMap("select * from avaliacao");
        assertThat(avaliacao.get("avaliador_id")).isEqualTo(maria.getId());
        assertThat(avaliacao.get("avaliado_id")).isEqualTo(carlos.getId());
        assertThat(avaliacao.get("papel_avaliado")).isEqualTo("PRESTADOR");
        assertThat(mediaDoPrestador(carlos))
                .containsEntry("qtd_avaliacoes", 1)
                .containsEntry("media_avaliacoes", new BigDecimal("4.0"));
    }

    @Test
    void prestadorAvaliaComNotaEComentarioEAMediaApareceNoPerfilDoEmpregador() throws Exception {
        Anuncio servico = servicoConcluidoHa(Duration.ofDays(1));

        avaliar(carlos, servico, Map.of("nota", 5, "comentario", "  Muito atenciosa, pagou na hora.  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nota", is(5)))
                .andExpect(jsonPath("$.comentario", is("Muito atenciosa, pagou na hora.")));

        mockMvc.perform(get("/conta/perfil-empregador").header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaAvaliacoes", is(5.0)))
                .andExpect(jsonPath("$.qtdAvaliacoes", is(1)));
    }

    @Test
    void mediaJuntaAsAvaliacoesDeServicosDiferentes() throws Exception {
        avaliar(maria, servicoConcluidoHa(Duration.ofDays(1)), Map.of("nota", 5)).andExpect(status().isCreated());
        avaliar(maria, servicoConcluidoHa(Duration.ofDays(2)), Map.of("nota", 4)).andExpect(status().isCreated());
        avaliar(maria, servicoConcluidoHa(Duration.ofDays(3)), Map.of("nota", 4)).andExpect(status().isCreated());

        assertThat(mediaDoPrestador(carlos))
                .containsEntry("qtd_avaliacoes", 3)
                .containsEntry("media_avaliacoes", new BigDecimal("4.3"));
    }

    @Test
    void comentarioSemNotaERecusado() throws Exception {
        Anuncio servico = servicoConcluidoHa(Duration.ofDays(1));

        avaliar(maria, servico, Map.of("comentario", "Ótimo trabalho"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("COMENTARIO_SEM_NOTA")));
        avaliar(maria, servico, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        avaliar(maria, servico, Map.of("nota", 6))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        avaliar(maria, servico, Map.of("nota", 3, "comentario", "x".repeat(401)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        assertThat(jdbc.queryForObject("select count(*) from avaliacao", Long.class)).isZero();
    }

    @Test
    void segundaAvaliacaoDoMesmoServicoERecusada() throws Exception {
        Anuncio servico = servicoConcluidoHa(Duration.ofDays(1));
        avaliar(maria, servico, Map.of("nota", 5)).andExpect(status().isCreated());

        avaliar(maria, servico, Map.of("nota", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("AVALIACAO_DUPLICADA")));

        assertThat(mediaDoPrestador(carlos)).containsEntry("media_avaliacoes", new BigDecimal("5.0"));
    }

    @Test
    void foraDaJanelaDe7DiasERecusada() throws Exception {
        Anuncio servico = servicoConcluidoHa(Duration.ofDays(7).plusMinutes(1));

        avaliar(maria, servico, Map.of("nota", 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("JANELA_AVALIACAO_ENCERRADA")));
        consultarPendente(maria, servico)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeAvaliar", is(false)));
    }

    @Test
    void servicoNaoConcluidoERecusado() throws Exception {
        Anuncio servico = servico(StatusAnuncio.AGUARDANDO_VALIDACAO, null);

        avaliar(maria, servico, Map.of("nota", 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_CONCLUIDO")));
        consultarPendente(maria, servico)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servico.concluidoEm", nullValue()))
                .andExpect(jsonPath("$.avaliarAte", nullValue()))
                .andExpect(jsonPath("$.podeAvaliar", is(false)));
    }

    @Test
    void anuncioSemPrestadorEscolhidoRespondeSemOutraParte() throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, null);

        consultarPendente(maria, anuncio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servico.concluidoEm", nullValue()))
                .andExpect(jsonPath("$.outraParte", nullValue()))
                .andExpect(jsonPath("$.avaliarAte", nullValue()))
                .andExpect(jsonPath("$.podeAvaliar", is(false)));
        avaliar(maria, anuncio, Map.of("nota", 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_CONCLUIDO")));
    }

    @Test
    void terceiroNaoAvaliaNemConsulta() throws Exception {
        Anuncio servico = servicoConcluidoHa(Duration.ofDays(1));
        Usuario intruso = usuario("Joana Lima", "joana@example.com", "39053344705", PapelUsuario.PRESTADOR);

        avaliar(intruso, servico, Map.of("nota", 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
        consultarPendente(intruso, servico)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
    }

    @Test
    void consultaPendenteMostraAOutraParteEDeixaDeOferecerDepoisDeAvaliar() throws Exception {
        Anuncio servico = servicoConcluidoHa(Duration.ofDays(1));

        consultarPendente(maria, servico)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servico.id", is(servico.getId().toString())))
                .andExpect(jsonPath("$.servico.titulo", is("Instalar 3 tomadas")))
                .andExpect(jsonPath("$.servico.concluidoEm", notNullValue()))
                .andExpect(jsonPath("$.outraParte.nome", is("Carlos Souza")))
                .andExpect(jsonPath("$.outraParte.papel", is("PRESTADOR")))
                .andExpect(jsonPath("$.avaliarAte", notNullValue()))
                .andExpect(jsonPath("$.podeAvaliar", is(true)));
        consultarPendente(carlos, servico)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outraParte.nome", is("Maria da Silva")))
                .andExpect(jsonPath("$.outraParte.papel", is("EMPREGADOR")))
                .andExpect(jsonPath("$.podeAvaliar", is(true)));

        avaliar(maria, servico, Map.of("nota", 5)).andExpect(status().isCreated());

        consultarPendente(maria, servico).andExpect(jsonPath("$.podeAvaliar", is(false)));
        consultarPendente(carlos, servico).andExpect(jsonPath("$.podeAvaliar", is(true)));
    }

    private ResultActions avaliar(Usuario usuario, Anuncio servico, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post("/anuncios/{id}/avaliacoes", servico.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)));
    }

    private ResultActions consultarPendente(Usuario usuario, Anuncio servico) throws Exception {
        return mockMvc.perform(get("/anuncios/{id}/avaliacao-pendente", servico.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(usuario)));
    }

    private Anuncio servicoConcluidoHa(Duration tempo) {
        return servico(StatusAnuncio.CONCLUIDO, Instant.now().minus(tempo));
    }

    /** Anúncio de Maria com Carlos escolhido; {@code validadoEm} é quando o serviço foi concluído. */
    private Anuncio servico(StatusAnuncio status, Instant validadoEm) {
        Anuncio anuncio = anuncio(status, validadoEm);
        aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(carlos.getId())
                .status(status == StatusAnuncio.CONCLUIDO ? StatusAplicacao.CONCLUIDA
                        : StatusAplicacao.AGUARDANDO_VALIDACAO)
                .dataAplicacao(Instant.now().minus(Duration.ofDays(12)))
                .escolhidaEm(Instant.now().minus(Duration.ofDays(11)))
                .build());
        return anuncio;
    }

    /** Anúncio de Maria ainda sem ninguém escolhido. */
    private Anuncio anuncio(StatusAnuncio status, Instant validadoEm) {
        return anuncioRepository.save(Anuncio.builder()
                .titulo("Instalar 3 tomadas")
                .categoriaId(jdbc.queryForObject("select id from categoria where protegida", UUID.class))
                .contratanteId(maria.getId())
                .descricao("Trocar três tomadas da cozinha")
                .dataHoraPretendida(Instant.now().minus(Duration.ofDays(10)))
                .status(status)
                .validadoEm(validadoEm)
                .build());
    }

    private Map<String, Object> mediaDoPrestador(Usuario prestador) {
        return jdbc.queryForMap("select media_avaliacoes, qtd_avaliacoes from perfil_prestador where usuario_id = ?",
                prestador.getId());
    }

    private void perfilDeAtuacao(Usuario prestador) {
        jdbc.update("""
                insert into perfil_prestador (usuario_id, raio_atendimento_km, municipio_base, uf_base, latitude_base,
                    longitude_base, completo)
                values (?, 10, 'São Paulo', 'SP', -23.55, -46.63, false)
                """, prestador.getId());
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
