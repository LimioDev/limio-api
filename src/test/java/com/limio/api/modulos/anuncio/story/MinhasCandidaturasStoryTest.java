package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.limio.api.comum.eventos.CandidaturaEncerradaEvent;
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
import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatRepository;
import com.limio.api.modulos.chat.enums.MotivoSomenteLeitura;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do ETI-77 — tela "Aplicações" do Prestador: listar por aba e
 * desistir de uma candidatura pendente. Anúncios e candidaturas nascem direto pelo repository.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class MinhasCandidaturasStoryTest {

    private static final String ROTA_LISTAR = "/candidaturas/minhas";
    private static final String SENHA = "senhaForte123";
    private static final String ENDERECO_COMPLETO = "Rua dos Pinheiros, 1000, apto 42";
    private static final String EMAIL_EMPREGADOR = "maria@example.com";
    private static final String TELEFONE_EMPREGADOR = "11988887777";

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
    private ChatRepository chatRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationEvents eventos;

    private Usuario empregador;
    private Usuario prestador;
    private String tokenPrestador;

    @BeforeEach
    void prepararUsuarios() throws Exception {
        limparBase();
        empregador = cadastrar("Maria da Silva", EMAIL_EMPREGADOR, TELEFONE_EMPREGADOR, "52998224725",
                PapelUsuario.EMPREGADOR);
        prestador = cadastrar("Rafael Souza", "rafael@example.com", "11999998888", "11144477735",
                PapelUsuario.PRESTADOR);
        tokenPrestador = logar("rafael@example.com");
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private void limparBase() {
        jdbc.execute("delete from mensagem");
        chatRepository.deleteAll();
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from sessao");
        jdbc.execute("delete from tentativa_login");
        usuarioRepository.deleteAll();
    }

    private Usuario cadastrar(String nomeCompleto, String email, String telefone, String cpf, PapelUsuario papel) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nomeCompleto)
                .email(email)
                .telefone(telefone)
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(25))
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

    private Anuncio anuncio(String titulo, StatusAnuncio status) {
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(empregador.getId())
                .titulo(titulo)
                .categoriaId(UUID.randomUUID()) // sem FK pra categoria (ADR-0001 §1.6): qualquer id serve aqui
                .descricao(titulo)
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(3)))
                .bairroRegiaoSnapshot("Pinheiros")
                .enderecoCompletoSnapshot(ENDERECO_COMPLETO)
                .status(status)
                .build());
    }

    private Aplicacao aplicacao(Anuncio anuncio, UUID prestadorId, StatusAplicacao status, Instant dataAplicacao) {
        return aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(prestadorId)
                .status(status)
                .dataAplicacao(dataAplicacao)
                .build());
    }

    private Aplicacao aplicacao(Anuncio anuncio, StatusAplicacao status) {
        return aplicacao(anuncio, prestador.getId(), status, Instant.now());
    }

    private ResultActions listar(String query) throws Exception {
        return mockMvc.perform(get(ROTA_LISTAR + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPrestador));
    }

    private ResultActions desistir(UUID candidaturaId) throws Exception {
        return mockMvc.perform(post("/candidaturas/{id}/desistir", candidaturaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPrestador));
    }

    // ---------- listar ----------

    @Test
    void dadoCandidaturasEmVariosStatus_quandoListaPendentes_entaoSoEnviadasComDadosDoCard() throws Exception {
        Anuncio faxina = anuncio("Faxina pós-obra", StatusAnuncio.PUBLICADO);
        Aplicacao enviada = aplicacao(faxina, StatusAplicacao.ENVIADA);
        aplicacao(anuncio("Passar roupa", StatusAnuncio.EM_ANDAMENTO), StatusAplicacao.ESCOLHIDA);
        aplicacao(anuncio("Jardim", StatusAnuncio.CONCLUIDO), StatusAplicacao.CONCLUIDA);

        listar("?aba=PENDENTES")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].id", is(enviada.getId().toString())))
                .andExpect(jsonPath("$.itens[0].anuncioId", is(faxina.getId().toString())))
                .andExpect(jsonPath("$.itens[0].titulo", is("Faxina pós-obra")))
                .andExpect(jsonPath("$.itens[0].dataHoraPretendida").exists())
                .andExpect(jsonPath("$.itens[0].nomeEmpregador", is("Maria S.")))
                .andExpect(jsonPath("$.itens[0].iniciaisEmpregador", is("MS")))
                .andExpect(jsonPath("$.itens[0].status", is("ENVIADA")))
                .andExpect(jsonPath("$.pagina", is(0)))
                .andExpect(jsonPath("$.totalItens", is(1)))
                .andExpect(jsonPath("$.totalPaginas", is(1)));
    }

    @Test
    void dadoCandidaturasEscolhidas_quandoListaCombinadas_entaoEscolhidaEAguardandoValidacao() throws Exception {
        aplicacao(anuncio("Faxina", StatusAnuncio.PUBLICADO), StatusAplicacao.ENVIADA);
        aplicacao(anuncio("Passar roupa", StatusAnuncio.EM_ANDAMENTO), StatusAplicacao.ESCOLHIDA);
        aplicacao(anuncio("Jardim", StatusAnuncio.AGUARDANDO_VALIDACAO), StatusAplicacao.AGUARDANDO_VALIDACAO);

        listar("?aba=COMBINADAS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[*].status").value(
                        containsInAnyOrder("ESCOLHIDA", "AGUARDANDO_VALIDACAO")));
    }

    @Test
    void dadoServicosEncerrados_quandoListaHistorico_entaoRecusadaConcluidaEEncerrada() throws Exception {
        aplicacao(anuncio("Faxina", StatusAnuncio.PUBLICADO), StatusAplicacao.ENVIADA);
        aplicacao(anuncio("Passar roupa", StatusAnuncio.EM_ANDAMENTO), StatusAplicacao.RECUSADA);
        aplicacao(anuncio("Jardim", StatusAnuncio.CONCLUIDO), StatusAplicacao.CONCLUIDA);
        aplicacao(anuncio("Pintura", StatusAnuncio.ENCERRADO), StatusAplicacao.ENCERRADA);

        listar("?aba=HISTORICO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(3)))
                .andExpect(jsonPath("$.itens[*].status").value(
                        containsInAnyOrder("RECUSADA", "CONCLUIDA", "ENCERRADA")));
    }

    @Test
    void dadoCandidaturaDeOutroPrestador_quandoLista_entaoNaoAparece() throws Exception {
        Usuario outro = cadastrar("Ana Lima", "ana@example.com", "11977776666", "10433218100",
                PapelUsuario.PRESTADOR);
        aplicacao(anuncio("Faxina", StatusAnuncio.PUBLICADO), outro.getId(), StatusAplicacao.ENVIADA, Instant.now());

        listar("?aba=PENDENTES")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(0)))
                .andExpect(jsonPath("$.totalItens", is(0)));
    }

    @Test
    void dadoAnuncioOndeNaoFoiEscolhido_quandoLista_entaoNuncaDevolveEnderecoNemContatos() throws Exception {
        Anuncio anuncio = anuncio("Faxina", StatusAnuncio.EM_ANDAMENTO);
        aplicacao(anuncio, StatusAplicacao.RECUSADA);
        aplicacao(anuncio("Passar roupa", StatusAnuncio.PUBLICADO), StatusAplicacao.ENVIADA);

        for (String aba : List.of("PENDENTES", "HISTORICO")) {
            listar("?aba=" + aba)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens", hasSize(1)))
                    .andExpect(content().string(not(containsString(ENDERECO_COMPLETO))))
                    .andExpect(content().string(not(containsString(EMAIL_EMPREGADOR))))
                    .andExpect(content().string(not(containsString(TELEFONE_EMPREGADOR))))
                    .andExpect(content().string(not(containsString("Maria da Silva"))));
        }
    }

    @Test
    void dadoMaisDeUmaPagina_quandoListaSegundaPagina_entaoRestoMaisAntigoPorUltimo() throws Exception {
        Instant base = Instant.now().minus(Duration.ofDays(30));
        for (int i = 0; i < 21; i++) {
            aplicacao(anuncio("Serviço " + i, StatusAnuncio.PUBLICADO), prestador.getId(), StatusAplicacao.ENVIADA,
                    base.plus(Duration.ofHours(i)));
        }

        listar("?aba=PENDENTES&pagina=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].titulo", is("Serviço 0")))
                .andExpect(jsonPath("$.pagina", is(1)))
                .andExpect(jsonPath("$.tamanho", is(20)))
                .andExpect(jsonPath("$.totalItens", is(21)))
                .andExpect(jsonPath("$.totalPaginas", is(2)));
    }

    @Test
    void dadoSemAba_quandoLista_entao400() throws Exception {
        listar("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoAbaInexistente_quandoLista_entao400() throws Exception {
        listar("?aba=ARQUIVADAS")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoPaginaNegativa_quandoLista_entao400() throws Exception {
        listar("?aba=PENDENTES&pagina=-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoSemToken_quandoLista_entao401() throws Exception {
        mockMvc.perform(get(ROTA_LISTAR + "?aba=PENDENTES"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }

    // ---------- desistir ----------

    @Test
    void dadoCandidaturaEnviada_quandoDesiste_entaoEncerraSomeDoEmpregadorEPublicaEvento() throws Exception {
        Anuncio anuncio = anuncio("Faxina", StatusAnuncio.PUBLICADO);
        Aplicacao enviada = aplicacao(anuncio, StatusAplicacao.ENVIADA);

        desistir(enviada.getId()).andExpect(status().isOk());

        Aplicacao salva = aplicacaoRepository.findById(enviada.getId()).orElseThrow();
        assertThat(salva.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(salva.getEncerradaEm()).isNotNull();
        assertThat(salva.getMotivoEncerramento()).isEqualTo("Desistência do Prestador");

        assertThat(eventos.stream(CandidaturaEncerradaEvent.class).toList()).singleElement()
                .satisfies(evento -> {
                    assertThat(evento.candidaturaId()).isEqualTo(enviada.getId());
                    assertThat(evento.anuncioId()).isEqualTo(anuncio.getId());
                    assertThat(evento.prestadorId()).isEqualTo(prestador.getId());
                });

        mockMvc.perform(get("/anuncios/painel-empregador")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar(EMAIL_EMPREGADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anuncios[0].candidatos", is(0)))
                .andExpect(jsonPath("$.novosCandidatos", is(0)));

        listar("?aba=PENDENTES").andExpect(jsonPath("$.itens", hasSize(0)));
        listar("?aba=HISTORICO").andExpect(jsonPath("$.itens[0].status", is("ENCERRADA")));
    }

    @Test
    void dadoChatDaCandidatura_quandoDesiste_entaoChatFicaSomenteLeitura() throws Exception {
        Anuncio anuncio = anuncio("Faxina", StatusAnuncio.PUBLICADO);
        Aplicacao enviada = aplicacao(anuncio, StatusAplicacao.ENVIADA);
        Chat chat = chatRepository.save(Chat.builder()
                .aplicacaoId(enviada.getId())
                .anuncioId(anuncio.getId())
                .contratanteId(empregador.getId())
                .prestadorId(prestador.getId())
                .build());

        desistir(enviada.getId()).andExpect(status().isOk());

        Chat salvo = chatRepository.findById(chat.getId()).orElseThrow();
        assertThat(salvo.isSomenteLeitura()).isTrue();
        assertThat(salvo.getMotivoSomenteLeitura()).isEqualTo(MotivoSomenteLeitura.CANDIDATO_DESISTIU);
    }

    @Test
    void dadoCandidaturaEscolhida_quandoDesiste_entao409IndicandoCancelamentoDoServico() throws Exception {
        Aplicacao escolhida = aplicacao(anuncio("Faxina", StatusAnuncio.EM_ANDAMENTO), StatusAplicacao.ESCOLHIDA);

        desistir(escolhida.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("USE_CANCELAMENTO_SERVICO")));

        assertThat(aplicacaoRepository.findById(escolhida.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAplicacao.ESCOLHIDA);
        assertThat(eventos.stream(CandidaturaEncerradaEvent.class)).isEmpty();
    }

    @Test
    void dadoCandidaturaJaEncerrada_quandoDesiste_entao409EstadoInvalido() throws Exception {
        Aplicacao recusada = aplicacao(anuncio("Faxina", StatusAnuncio.EM_ANDAMENTO), StatusAplicacao.RECUSADA);

        desistir(recusada.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CANDIDATURA_ESTADO_INVALIDO")));
    }

    @Test
    void dadoCandidaturaDeOutroPrestador_quandoDesiste_entao404SemAlterar() throws Exception {
        Usuario outro = cadastrar("Ana Lima", "ana@example.com", "11977776666", "10433218100",
                PapelUsuario.PRESTADOR);
        Aplicacao alheia = aplicacao(anuncio("Faxina", StatusAnuncio.PUBLICADO), outro.getId(),
                StatusAplicacao.ENVIADA, Instant.now());

        desistir(alheia.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("CANDIDATURA_NAO_ENCONTRADA")));

        assertThat(aplicacaoRepository.findById(alheia.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAplicacao.ENVIADA);
    }

    @Test
    void dadoCandidaturaInexistente_quandoDesiste_entao404() throws Exception {
        desistir(UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("CANDIDATURA_NAO_ENCONTRADA")));
    }

    @Test
    void dadoSemToken_quandoDesiste_entao401() throws Exception {
        mockMvc.perform(post("/candidaturas/{id}/desistir", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }
}
