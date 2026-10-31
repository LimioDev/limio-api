package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.CandidaturaRecusadaEvent;
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
 * BDD ponta a ponta (controller -> banco real) do UC "Recusar um candidato" (ETI-67). Anúncios e candidaturas nascem
 * direto pelo repository e os chats por SQL (o listener que cria o chat só roda no fluxo de candidatar-se).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class RecusarCandidatoStoryTest {

    private static final String SENHA = "senhaForte123";

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

    @Autowired
    private ApplicationEvents eventos;

    @BeforeEach
    void limparBase() {
        jdbc.execute("delete from mensagem");
        jdbc.execute("delete from chat");
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
                .telefone("11988887777")
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

    private Anuncio anuncio(UUID contratanteId, StatusAnuncio status) {
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(contratanteId)
                .titulo("Instalar 3 tomadas")
                .categoriaId(UUID.randomUUID()) // sem FK pra categoria (ADR-0001 §1.6)
                .descricao("Instalar 3 tomadas novas na sala e trocar o disjuntor")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(5)))
                .bairroRegiaoSnapshot("Pinheiros")
                .status(status)
                .build());
    }

    private Aplicacao aplicacao(UUID anuncioId, UUID prestadorId, StatusAplicacao status) {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(prestadorId)
                .status(status)
                .dataAplicacao(Instant.now())
                .build();
        if (status == StatusAplicacao.ESCOLHIDA) {
            aplicacao.setEscolhidaEm(Instant.now());
        }
        return aplicacaoRepository.save(aplicacao);
    }

    private void chat(Aplicacao aplicacao, UUID contratanteId) {
        jdbc.update("insert into chat (aplicacao_id, anuncio_id, contratante_id, prestador_id) values (?, ?, ?, ?)",
                aplicacao.getId(), aplicacao.getAnuncioId(), contratanteId, aplicacao.getPrestadorId());
    }

    private ResultActions recusar(String token, UUID anuncioId, UUID candidaturaId) throws Exception {
        return mockMvc.perform(post("/anuncios/{anuncioId}/candidaturas/{candidaturaId}/recusar", anuncioId,
                candidaturaId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Aplicacao recarregar(Aplicacao aplicacao) {
        return aplicacaoRepository.findById(aplicacao.getId()).orElseThrow();
    }

    @Test
    void dadaCandidaturaEnviada_quandoRecusa_entao200ComListaSemOsRecusadosEPublicaEvento() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Usuario ana = usuario("Ana Lima", "ana@example.com", "16899535009", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        Aplicacao daAna = aplicacao(anuncio.getId(), ana.getId(), StatusAplicacao.ENVIADA);

        recusar(logar("maria@example.com"), anuncio.getId(), doRafael.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatos", hasSize(1)))
                .andExpect(jsonPath("$.candidatos[0].candidaturaId", is(daAna.getId().toString())));

        Aplicacao recusada = recarregar(doRafael);
        assertThat(recusada.getStatus()).isEqualTo(StatusAplicacao.RECUSADA);
        assertThat(recusada.getRecusadaEm()).isNotNull();
        // os demais candidatos continuam como estavam
        Aplicacao outra = recarregar(daAna);
        assertThat(outra.getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        assertThat(outra.getRecusadaEm()).isNull();

        List<CandidaturaRecusadaEvent> publicados = eventos.stream(CandidaturaRecusadaEvent.class).toList();
        assertThat(publicados).hasSize(1);
        CandidaturaRecusadaEvent evento = publicados.get(0);
        assertThat(evento.candidaturaId()).isEqualTo(doRafael.getId());
        assertThat(evento.anuncioId()).isEqualTo(anuncio.getId());
        assertThat(evento.anuncioTitulo()).isEqualTo("Instalar 3 tomadas");
        assertThat(evento.contratanteId()).isEqualTo(maria.getId());
        assertThat(evento.prestadorId()).isEqualTo(rafael.getId());
    }

    @Test
    void dadaCandidaturaComChat_quandoRecusa_entaoSoOChatDelaFicaSomenteLeitura() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Usuario ana = usuario("Ana Lima", "ana@example.com", "16899535009", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        Aplicacao daAna = aplicacao(anuncio.getId(), ana.getId(), StatusAplicacao.ENVIADA);
        chat(doRafael, maria.getId());
        chat(daAna, maria.getId());

        recusar(logar("maria@example.com"), anuncio.getId(), doRafael.getId()).andExpect(status().isOk());

        // o listener do chat roda logo depois do commit, ainda dentro da requisição
        assertThat(jdbc.queryForMap("select somente_leitura, motivo_somente_leitura, somente_leitura_em "
                + "from chat where aplicacao_id = ?", doRafael.getId()))
                .containsEntry("somente_leitura", true)
                .containsEntry("motivo_somente_leitura", "CANDIDATURA_RECUSADA")
                .doesNotContainEntry("somente_leitura_em", null);
        assertThat(jdbc.queryForMap("select somente_leitura, motivo_somente_leitura from chat where aplicacao_id = ?",
                daAna.getId()))
                .containsEntry("somente_leitura", false)
                .containsEntry("motivo_somente_leitura", null);
    }

    @Test
    void dadoUnicoCandidato_quandoRecusa_entaoAnuncioContinuaPublicadoESemEscolhido() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao unica = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);

        recusar(logar("maria@example.com"), anuncio.getId(), unica.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatos", hasSize(0)));

        assertThat(anuncioRepository.findById(anuncio.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(aplicacaoRepository.findAll())
                .noneMatch(aplicacao -> aplicacao.getStatus().isEscolhida())
                .extracting(Aplicacao::getStatus).containsExactly(StatusAplicacao.RECUSADA);
    }

    @Test
    void dadaCandidaturaJaRecusada_quandoRecusaDeNovo_entao409EUmSoEvento() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao candidatura = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        String token = logar("maria@example.com");

        recusar(token, anuncio.getId(), candidatura.getId()).andExpect(status().isOk());
        Instant primeiraRecusa = recarregar(candidatura).getRecusadaEm();

        recusar(token, anuncio.getId(), candidatura.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CANDIDATURA_ESTADO_INVALIDO")));

        assertThat(recarregar(candidatura).getRecusadaEm()).isEqualTo(primeiraRecusa);
        assertThat(eventos.stream(CandidaturaRecusadaEvent.class).count()).isEqualTo(1);
    }

    @Test
    void dadaCandidaturaEncerrada_quandoRecusa_entao409() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao candidatura = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENCERRADA);

        recusar(logar("maria@example.com"), anuncio.getId(), candidatura.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CANDIDATURA_ESTADO_INVALIDO")));

        assertThat(recarregar(candidatura).getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(eventos.stream(CandidaturaRecusadaEvent.class).count()).isZero();
    }

    @Test
    void dadaCandidaturaEscolhida_quandoRecusa_entao409() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao candidatura = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ESCOLHIDA);

        recusar(logar("maria@example.com"), anuncio.getId(), candidatura.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CANDIDATURA_ESTADO_INVALIDO")));

        assertThat(recarregar(candidatura).getStatus()).isEqualTo(StatusAplicacao.ESCOLHIDA);
        assertThat(eventos.stream(CandidaturaRecusadaEvent.class).count()).isZero();
    }

    @Test
    void dadoAnuncioDeOutroUsuario_quandoRecusa_entao404SemMudarNada() throws Exception {
        usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario joao = usuario("João Pereira", "joao@example.com", "11144477735", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Anuncio anuncioDeJoao = anuncio(joao.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao candidatura = aplicacao(anuncioDeJoao.getId(), rafael.getId(), StatusAplicacao.ENVIADA);

        recusar(logar("maria@example.com"), anuncioDeJoao.getId(), candidatura.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_NAO_ENCONTRADO")));

        assertThat(recarregar(candidatura).getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        assertThat(eventos.stream(CandidaturaRecusadaEvent.class).count()).isZero();
    }

    @Test
    void dadaCandidaturaDeOutroAnuncio_quandoRecusa_entao404() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Anuncio outroAnuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doOutro = aplicacao(outroAnuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);

        recusar(logar("maria@example.com"), anuncio.getId(), doOutro.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("CANDIDATURA_NAO_ENCONTRADA")));

        assertThat(recarregar(doOutro).getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
    }

    @Test
    void dadaCandidaturaInexistente_quandoRecusa_entao404() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);

        recusar(logar("maria@example.com"), anuncio.getId(), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("CANDIDATURA_NAO_ENCONTRADA")));
    }

    @Test
    void dadoAnuncioEncerrado_quandoRecusa_entao409() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.ENCERRADO);
        Aplicacao candidatura = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);

        recusar(logar("maria@example.com"), anuncio.getId(), candidatura.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_ESTADO_INVALIDO")));

        assertThat(recarregar(candidatura).getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
    }

    @Test
    void dadoSemToken_quandoRecusa_entao401() throws Exception {
        mockMvc.perform(post("/anuncios/{anuncioId}/candidaturas/{candidaturaId}/recusar", UUID.randomUUID(),
                        UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }
}
