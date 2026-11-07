package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.PrestadorEscolhidoEvent;
import com.limio.api.comum.portas.ConsultaPlanoPrestador;
import com.limio.api.comum.portas.enums.Plano;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.sessao.SessaoRepository;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensal;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller → banco real) do UC "Escolher prestador" (ETI-69). Perfil de atuação e cota entram por
 * SQL (a FK de {@code cota_mensal} exige o perfil). O plano é um {@link MockitoBean}: o {@code financeiro} ainda é
 * stub e sempre devolveria FREE. As corridas usam threads reais e conferem o ESTADO FINAL do banco (uma escolhida,
 * demais nos estados certos, cota consumida uma única vez), não só os códigos HTTP.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class EscolherPrestadorStoryTest {

    private static final String SENHA = "senhaForte123";
    private static final int TETO = 4;

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

    @MockitoBean
    private ConsultaPlanoPrestador consultaPlanoPrestador;

    @MockitoSpyBean
    private AplicacaoService aplicacaoService;

    @BeforeEach
    void limparBaseEPlanoFree() {
        limparBase();
        when(consultaPlanoPrestador.planoAtual(any())).thenReturn(Plano.FREE);
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private void limparBase() {
        jdbc.execute("delete from mensagem");
        jdbc.execute("delete from chat");
        jdbc.execute("delete from cota_mensal");
        jdbc.execute("delete from perfil_prestador");
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
        sessaoRepository.deleteAll();
        usuarioRepository.deleteAll();
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
                .emailConfirmadoEm(Instant.now())
                .telefoneConfirmadoEm(Instant.now())
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
        UUID categoriaId = jdbc.queryForObject("select id from categoria where protegida", UUID.class);
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(contratanteId)
                .titulo("Instalar 3 tomadas")
                .categoriaId(categoriaId)
                .descricao("Instalar 3 tomadas novas na sala e trocar o disjuntor")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(5)))
                .bairroRegiaoSnapshot("Pinheiros")
                .enderecoCompletoSnapshot("Rua dos Pinheiros, 100, Pinheiros, São Paulo - SP")
                .status(status)
                .build());
    }

    private Aplicacao aplicacao(UUID anuncioId, UUID prestadorId, StatusAplicacao status) {
        return aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(prestadorId)
                .status(status)
                .dataAplicacao(Instant.now())
                .build());
    }

    private UUID perfil(UUID usuarioId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into perfil_prestador (id, usuario_id, raio_atendimento_km, municipio_base, uf_base,
                    latitude_base, longitude_base, completo)
                values (?, ?, 10, 'Campinas', 'SP', -22.9, -47.06, true)""", id, usuarioId);
        return id;
    }

    /** Prestador com perfil e {@code consumidas} escolhas já gastas neste mês (mês civil de São Paulo). */
    private Usuario prestador(String nome, String email, String cpf, int consumidas) {
        Usuario prestador = usuario(nome, email, cpf, PapelUsuario.PRESTADOR);
        UUID perfilId = perfil(prestador.getId());
        if (consumidas > 0) {
            jdbc.update("insert into cota_mensal (perfil_prestador_id, ano_mes, escolhas_consumidas, teto_aplicado) "
                    + "values (?, ?, ?, ?)", perfilId, CotaMensal.mesDe(Instant.now()).toString(), consumidas, TETO);
        }
        return prestador;
    }

    /** Escolhas consumidas no mês corrente pelo Prestador; 0 se ainda não há linha. */
    private int consumidas(Usuario prestador) {
        List<Integer> linhas = jdbc.queryForList("""
                select c.escolhas_consumidas from cota_mensal c
                join perfil_prestador p on p.id = c.perfil_prestador_id
                where p.usuario_id = ? and c.ano_mes = ?""", Integer.class, prestador.getId(),
                CotaMensal.mesDe(Instant.now()).toString());
        return linhas.isEmpty() ? 0 : linhas.get(0);
    }

    private ResultActions escolher(String token, UUID anuncioId, UUID candidaturaId) throws Exception {
        return mockMvc.perform(post("/anuncios/{anuncioId}/candidaturas/{candidaturaId}/escolher", anuncioId,
                candidaturaId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private int escolherCodigo(String token, UUID anuncioId, UUID candidaturaId) throws Exception {
        return escolher(token, anuncioId, candidaturaId).andReturn().getResponse().getStatus();
    }

    private Aplicacao recarregar(Aplicacao aplicacao) {
        return aplicacaoRepository.findById(aplicacao.getId()).orElseThrow();
    }

    private long contar(UUID anuncioId, StatusAplicacao status) {
        return aplicacaoRepository.findAll().stream()
                .filter(a -> a.getAnuncioId().equals(anuncioId) && a.getStatus() == status).count();
    }

    private static void pausa(Duration duracao) {
        try {
            Thread.sleep(duracao);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------- caminho feliz

    @Test
    void dadoPrestadorFreeComCota_quandoEscolhe_entao200EncerraAsDemaisPreservaRecusadaEConsomeCota() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Usuario ana = prestador("Ana Lima", "ana@example.com", "16899535009", 0);
        Usuario joao = prestador("João Pereira", "joao@example.com", "15350946056", 0);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        Aplicacao daAna = aplicacao(anuncio.getId(), ana.getId(), StatusAplicacao.ENVIADA);
        Aplicacao doJoao = aplicacao(anuncio.getId(), joao.getId(), StatusAplicacao.RECUSADA);
        doJoao.setRecusadaEm(Instant.now());
        aplicacaoRepository.save(doJoao);

        escolher(logar("maria@example.com"), anuncio.getId(), doRafael.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anuncioId", is(anuncio.getId().toString())));

        Aplicacao escolhida = recarregar(doRafael);
        assertThat(escolhida.getStatus()).isEqualTo(StatusAplicacao.ESCOLHIDA);
        assertThat(escolhida.getEscolhidaEm()).isNotNull();
        Aplicacao encerrada = recarregar(daAna);
        assertThat(encerrada.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(encerrada.getEncerradaEm()).isNotNull();
        assertThat(encerrada.getMotivoEncerramento()).isEqualTo("Outro prestador escolhido");
        assertThat(recarregar(doJoao).getStatus()).isEqualTo(StatusAplicacao.RECUSADA);
        assertThat(recarregar(doJoao).getEncerradaEm()).isNull();
        assertThat(anuncioRepository.findById(anuncio.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(consumidas(rafael)).isEqualTo(4);
        assertThat(consumidas(ana)).isZero();

        List<PrestadorEscolhidoEvent> publicados = eventos.stream(PrestadorEscolhidoEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).candidaturaEscolhidaId()).isEqualTo(doRafael.getId());
        assertThat(publicados.get(0).candidaturasNaoEscolhidasIds()).containsExactly(daAna.getId());
    }

    @Test
    void dadoPrestadorGoldAtivoSemCota_quandoEscolhe_entao200SemConsumirCota() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", TETO);
        when(consultaPlanoPrestador.planoAtual(rafael.getId())).thenReturn(Plano.GOLD);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);

        escolher(logar("maria@example.com"), anuncio.getId(), doRafael.getId()).andExpect(status().isOk());

        assertThat(recarregar(doRafael).getStatus()).isEqualTo(StatusAplicacao.ESCOLHIDA);
        assertThat(consumidas(rafael)).isEqualTo(TETO);
    }

    // ---------------------------------------------------------------- cota esgotada

    @Test
    void dadoPrestadorFreeSemCota_quandoEscolhe_entao409ComONomeENadaMuda() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael da Silva Souza", "rafael@example.com", "39053344705", TETO);
        Usuario ana = prestador("Ana Lima", "ana@example.com", "16899535009", 0);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        Aplicacao daAna = aplicacao(anuncio.getId(), ana.getId(), StatusAplicacao.ENVIADA);
        String token = logar("maria@example.com");

        escolher(token, anuncio.getId(), doRafael.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("COTA_ESGOTADA_CANDIDATO")))
                .andExpect(jsonPath("$.mensagem", containsString("Rafael S.")));

        assertThat(recarregar(doRafael).getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        assertThat(recarregar(doRafael).getEscolhidaEm()).isNull();
        assertThat(recarregar(daAna).getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        assertThat(consumidas(rafael)).isEqualTo(TETO);
        assertThat(eventos.stream(PrestadorEscolhidoEvent.class).count()).isZero();

        // o Empregador segue podendo escolher outro candidato
        escolher(token, anuncio.getId(), daAna.getId()).andExpect(status().isOk());
        assertThat(recarregar(daAna).getStatus()).isEqualTo(StatusAplicacao.ESCOLHIDA);
    }

    // ---------------------------------------------------------------- validações

    @Test
    void dadaCandidaturaDeOutroAnuncio_quandoEscolhe_entao404SemAlterarNada() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 0);
        Usuario ana = prestador("Ana Lima", "ana@example.com", "16899535009", 0);
        Anuncio meu = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Anuncio outro = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao minha = aplicacao(meu.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        Aplicacao deOutroAnuncio = aplicacao(outro.getId(), ana.getId(), StatusAplicacao.ENVIADA);

        escolher(logar("maria@example.com"), meu.getId(), deOutroAnuncio.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("CANDIDATURA_NAO_ENCONTRADA")));

        assertThat(recarregar(minha).getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        assertThat(recarregar(deOutroAnuncio).getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        assertThat(consumidas(ana)).isZero();
    }

    @Test
    void dadoEmpregadorQueNaoEDono_quandoEscolhe_entao404() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        usuario("Pedro Alves", "pedro@example.com", "11144477735", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 0);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);

        escolher(logar("pedro@example.com"), anuncio.getId(), doRafael.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_NAO_ENCONTRADO")));

        assertThat(recarregar(doRafael).getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
    }

    @Test
    void dadoAnuncioForaDePublicado_quandoEscolhe_entao409() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 0);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.ENCERRADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);

        escolher(logar("maria@example.com"), anuncio.getId(), doRafael.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_ESTADO_INVALIDO")));
    }

    @Test
    void dadaCandidaturaRecusada_quandoEscolhe_entao409ECandidaturaContinuaRecusada() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 0);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao recusada = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.RECUSADA);

        escolher(logar("maria@example.com"), anuncio.getId(), recusada.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CANDIDATURA_ESTADO_INVALIDO")));

        assertThat(recarregar(recusada).getStatus()).isEqualTo(StatusAplicacao.RECUSADA);
        assertThat(consumidas(rafael)).isZero();
    }

    @Test
    void dadoSemToken_quandoEscolhe_entao401() throws Exception {
        mockMvc.perform(post("/anuncios/{anuncioId}/candidaturas/{candidaturaId}/escolher", UUID.randomUUID(),
                        UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }

    @Test
    void dadaPrimeiraEscolhaConfirmada_quandoEscolheOutraDepois_entao409EscolhaConflitoESoUmaEscolhida()
            throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 0);
        Usuario ana = prestador("Ana Lima", "ana@example.com", "16899535009", 0);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        Aplicacao daAna = aplicacao(anuncio.getId(), ana.getId(), StatusAplicacao.ENVIADA);
        String token = logar("maria@example.com");

        escolher(token, anuncio.getId(), doRafael.getId()).andExpect(status().isOk());
        escolher(token, anuncio.getId(), daAna.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ESCOLHA_CONFLITO")));

        assertThat(contar(anuncio.getId(), StatusAplicacao.ESCOLHIDA)).isEqualTo(1);
        assertThat(recarregar(daAna).getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(consumidas(rafael)).isEqualTo(1);
        assertThat(consumidas(ana)).isZero();
    }

    // ---------------------------------------------------------------- liberação de contato

    @Test
    void dadaEscolha_quandoConsultaOServico_entaoSoOEscolhidoVeEnderecoETelefoneEOsDemais404() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 0);
        Usuario ana = prestador("Ana Lima", "ana@example.com", "16899535009", 0);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        aplicacao(anuncio.getId(), ana.getId(), StatusAplicacao.ENVIADA);
        String tokenRafael = logar("rafael@example.com");
        String tokenAna = logar("ana@example.com");

        // antes da escolha ninguém além do dono enxerga o serviço
        mockMvc.perform(get("/anuncios/{id}/servico", anuncio.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRafael)).andExpect(status().isNotFound());

        escolher(logar("maria@example.com"), anuncio.getId(), doRafael.getId()).andExpect(status().isOk());

        mockMvc.perform(get("/anuncios/{id}/servico", anuncio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRafael))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enderecoCompleto", is("Rua dos Pinheiros, 100, Pinheiros, São Paulo - SP")))
                .andExpect(jsonPath("$.outraParte.telefone", is("+5511988887777")))
                .andExpect(jsonPath("$.escolhidaEm", notNullValue()));
        mockMvc.perform(get("/anuncios/{id}/servico", anuncio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAna))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
    }

    // ---------------------------------------------------------------- concorrência

    /**
     * Duas escolhas de candidatos diferentes disparadas juntas no mesmo anúncio. Só a primeira confirmada prevalece
     * (200) e a outra recebe 409. O banco termina com UMA escolhida, a outra ENCERRADA e a cota consumida uma vez.
     */
    @Test
    void dadasDuasEscolhasSimultaneasNoMesmoAnuncio_entaoUmaVenceAOutraRecebe409EOBancoFicaConsistente()
            throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 0);
        Usuario ana = prestador("Ana Lima", "ana@example.com", "16899535009", 0);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        Aplicacao daAna = aplicacao(anuncio.getId(), ana.getId(), StatusAplicacao.ENVIADA);
        String token = logar("maria@example.com");
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = executor.submit(() -> {
                largada.await();
                return escolherCodigo(token, anuncio.getId(), doRafael.getId());
            });
            Future<Integer> b = executor.submit(() -> {
                largada.await();
                return escolherCodigo(token, anuncio.getId(), daAna.getId());
            });
            largada.countDown();

            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            largada.countDown();
            executor.shutdownNow();
        }

        assertThat(contar(anuncio.getId(), StatusAplicacao.ESCOLHIDA)).isEqualTo(1);
        assertThat(contar(anuncio.getId(), StatusAplicacao.ENCERRADA)).isEqualTo(1);
        assertThat(contar(anuncio.getId(), StatusAplicacao.ENVIADA)).isZero();
        assertThat(consumidas(rafael) + consumidas(ana)).isEqualTo(1);
        boolean rafaelVenceu = recarregar(doRafael).getStatus() == StatusAplicacao.ESCOLHIDA;
        assertThat(consumidas(rafaelVenceu ? rafael : ana)).isEqualTo(1);
        assertThat(eventos.stream(PrestadorEscolhidoEvent.class).count()).isEqualTo(1);
    }

    /**
     * O mesmo Prestador, com 3 de 4 escolhas usadas, é escolhido ao mesmo tempo em dois anúncios de Empregadores
     * diferentes. O lock do anúncio é por anúncio e não os enfileira; a cota do Prestador sim: só uma passa e a
     * cota fecha em 4, nunca 5.
     */
    @Test
    void dadoMesmoPrestadorComUmaVagaEmDoisAnuncios_quandoEscolhidoJuntos_entaoSoUmaPassaECotaFechaEmQuatro()
            throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario pedro = usuario("Pedro Alves", "pedro@example.com", "11144477735", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", TETO - 1);
        Anuncio anuncioDaMaria = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Anuncio anuncioDoPedro = anuncio(pedro.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao paraMaria = aplicacao(anuncioDaMaria.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        Aplicacao paraPedro = aplicacao(anuncioDoPedro.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        String tokenMaria = logar("maria@example.com");
        String tokenPedro = logar("pedro@example.com");
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = executor.submit(() -> {
                largada.await();
                return escolherCodigo(tokenMaria, anuncioDaMaria.getId(), paraMaria.getId());
            });
            Future<Integer> b = executor.submit(() -> {
                largada.await();
                return escolherCodigo(tokenPedro, anuncioDoPedro.getId(), paraPedro.getId());
            });
            largada.countDown();

            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            largada.countDown();
            executor.shutdownNow();
        }

        assertThat(consumidas(rafael)).isEqualTo(TETO);
        long escolhidas = List.of(paraMaria, paraPedro).stream()
                .filter(c -> recarregar(c).getStatus() == StatusAplicacao.ESCOLHIDA).count();
        assertThat(escolhidas).isEqualTo(1);
        long aindaEnviadas = List.of(paraMaria, paraPedro).stream()
                .filter(c -> recarregar(c).getStatus() == StatusAplicacao.ENVIADA).count();
        assertThat(aindaEnviadas).isEqualTo(1);
    }

    /**
     * Escolha e desistência do MESMO candidato ao mesmo tempo. Ou a escolha vence (200, ESCOLHIDA, cota 1, a
     * desistência cai em 409 porque agora é cancelar o serviço) ou a desistência vence (200, ENCERRADA, cota 0, a
     * escolha cai em 409). Nunca uma escolha por cima de uma desistência.
     */
    @Test
    void dadasEscolhaEDesistenciaSimultaneasDoMesmoCandidato_entaoEstadoFinalEDoVencedorECotaCoerente()
            throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 0);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        String tokenMaria = logar("maria@example.com");
        String tokenRafael = logar("rafael@example.com");
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int codigoEscolha;
        int codigoDesistencia;
        try {
            Future<Integer> escolha = executor.submit(() -> {
                largada.await();
                return escolherCodigo(tokenMaria, anuncio.getId(), doRafael.getId());
            });
            Future<Integer> desistencia = executor.submit(() -> {
                largada.await();
                return mockMvc.perform(post("/candidaturas/{id}/desistir", doRafael.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRafael))
                        .andReturn().getResponse().getStatus();
            });
            largada.countDown();
            codigoEscolha = escolha.get(20, TimeUnit.SECONDS);
            codigoDesistencia = desistencia.get(20, TimeUnit.SECONDS);
        } finally {
            largada.countDown();
            executor.shutdownNow();
        }

        boolean escolhaVenceu = codigoEscolha == 200 && codigoDesistencia == 409;
        boolean desistenciaVenceu = codigoEscolha == 409 && codigoDesistencia == 200;
        assertThat(escolhaVenceu || desistenciaVenceu)
                .as("escolha=%d desistencia=%d", codigoEscolha, codigoDesistencia).isTrue();
        assertThat(recarregar(doRafael).getStatus())
                .isEqualTo(escolhaVenceu ? StatusAplicacao.ESCOLHIDA : StatusAplicacao.ENCERRADA);
        assertThat(consumidas(rafael)).isEqualTo(escolhaVenceu ? 1 : 0);
    }

    /** Escolha e recusa da mesma candidatura, juntas: uma vence, o estado final e a cota são coerentes. */
    @Test
    void dadasEscolhaERecusaSimultaneasDaMesmaCandidatura_entaoEstadoFinalEDoVencedorECotaCoerente()
            throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 0);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        String token = logar("maria@example.com");
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int codigoEscolha;
        int codigoRecusa;
        try {
            Future<Integer> escolha = executor.submit(() -> {
                largada.await();
                return escolherCodigo(token, anuncio.getId(), doRafael.getId());
            });
            Future<Integer> recusa = executor.submit(() -> {
                largada.await();
                return mockMvc.perform(post("/anuncios/{a}/candidaturas/{c}/recusar", anuncio.getId(),
                        doRafael.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andReturn().getResponse().getStatus();
            });
            largada.countDown();
            codigoEscolha = escolha.get(20, TimeUnit.SECONDS);
            codigoRecusa = recusa.get(20, TimeUnit.SECONDS);
        } finally {
            largada.countDown();
            executor.shutdownNow();
        }

        boolean escolhaVenceu = codigoEscolha == 200 && codigoRecusa == 409;
        boolean recusaVenceu = codigoEscolha == 409 && codigoRecusa == 200;
        assertThat(escolhaVenceu || recusaVenceu)
                .as("escolha=%d recusa=%d", codigoEscolha, codigoRecusa).isTrue();
        assertThat(recarregar(doRafael).getStatus())
                .isEqualTo(escolhaVenceu ? StatusAplicacao.ESCOLHIDA : StatusAplicacao.RECUSADA);
        assertThat(consumidas(rafael)).isEqualTo(escolhaVenceu ? 1 : 0);
    }

    /**
     * Uma candidatura nova chega enquanto o Empregador escolhe. Qualquer que seja a ordem, o anúncio nunca termina com
     * uma candidatura {@code ENVIADA} órfã ao lado de uma escolhida: a nova entra antes (e é encerrada junto) ou
     * depois (e é barrada com 409).
     */
    @Test
    void dadaCandidaturaNovaDuranteAEscolha_entaoNaoSobraEnviadaOrfaAoLadoDaEscolhida() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 0);
        // GOLD: o foco aqui é a corrida com a candidatura, e 5 rodadas estourariam o teto FREE de 4
        when(consultaPlanoPrestador.planoAtual(rafael.getId())).thenReturn(Plano.GOLD);
        usuario("Ana Lima", "ana@example.com", "16899535009", PapelUsuario.PRESTADOR);
        String tokenMaria = logar("maria@example.com");
        String tokenAna = logar("ana@example.com");

        for (int rodada = 0; rodada < 5; rodada++) {
            Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
            Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
            CountDownLatch largada = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> escolha = executor.submit(() -> {
                    largada.await();
                    return escolherCodigo(tokenMaria, anuncio.getId(), doRafael.getId());
                });
                Future<Integer> candidatura = executor.submit(() -> {
                    largada.await();
                    return mockMvc.perform(post("/anuncios/{a}/candidaturas", anuncio.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAna))
                            .andReturn().getResponse().getStatus();
                });
                largada.countDown();
                assertThat(escolha.get(20, TimeUnit.SECONDS)).isEqualTo(200);
                assertThat(candidatura.get(20, TimeUnit.SECONDS)).isIn(201, 409);
            } finally {
                largada.countDown();
                executor.shutdownNow();
            }

            assertThat(contar(anuncio.getId(), StatusAplicacao.ESCOLHIDA)).isEqualTo(1);
            assertThat(contar(anuncio.getId(), StatusAplicacao.ENVIADA))
                    .as("rodada %d: sobrou candidatura ENVIADA órfã", rodada).isZero();
        }
    }

    // ---------------------------------------------------------------- rollback

    /**
     * A gravação da escolha falha DEPOIS de a cota já ter sido consumida (e antes das demais mudanças). Tudo volta: a
     * cota não sobe, nenhuma candidatura muda, nenhum evento sai.
     */
    @Test
    void dadaFalhaAoGravarAEscolhaDepoisDeConsumirACota_entaoRollbackTotal() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 2);
        Usuario ana = prestador("Ana Lima", "ana@example.com", "16899535009", 0);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        Aplicacao daAna = aplicacao(anuncio.getId(), ana.getId(), StatusAplicacao.ENVIADA);
        doThrow(new IllegalStateException("falha simulada")).when(aplicacaoService).salvarEscolha(any());

        escolher(logar("maria@example.com"), anuncio.getId(), doRafael.getId())
                .andExpect(status().isInternalServerError());

        assertThat(consumidas(rafael)).isEqualTo(2);
        assertThat(recarregar(doRafael).getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        assertThat(recarregar(doRafael).getEscolhidaEm()).isNull();
        assertThat(recarregar(daAna).getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        assertThat(eventos.stream(PrestadorEscolhidoEvent.class).count()).isZero();
    }

    /** Segurar a trava do anúncio deixa a escolha em fila (espera, não falha) e ela conclui quando solta. */
    @Test
    void dadaTravaDoAnuncioSegurada_quandoEscolhe_entaoEscolhaEsperaEConcluiQuandoSolta() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 0);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao doRafael = aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        String token = logar("maria@example.com");
        CountDownLatch comTrava = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<?>> futuros = new ArrayList<>();
        try {
            futuros.add(executor.submit(() -> jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c -> {
                c.setAutoCommit(false);
                try (var st = c.prepareStatement(
                        "select pg_advisory_xact_lock(hashtext('ANUNCIO'), hashtext(?))")) {
                    st.setString(1, anuncio.getId().toString());
                    st.execute();
                    comTrava.countDown();
                    soltar.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    c.rollback();
                    c.setAutoCommit(true);
                }
                return null;
            })));
            assertThat(comTrava.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> escolha = executor.submit(() -> escolherCodigo(token, anuncio.getId(), doRafael.getId()));
            pausa(Duration.ofMillis(700));
            assertThat(escolha).isNotDone();
            assertThat(recarregar(doRafael).getStatus()).isEqualTo(StatusAplicacao.ENVIADA);

            soltar.countDown();
            assertThat(escolha.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            futuros.get(0).get(10, TimeUnit.SECONDS);
        } finally {
            soltar.countDown();
            executor.shutdownNow();
        }

        assertThat(recarregar(doRafael).getStatus()).isEqualTo(StatusAplicacao.ESCOLHIDA);
    }
}
