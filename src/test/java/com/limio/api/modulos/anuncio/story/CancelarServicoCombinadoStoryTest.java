package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
import com.limio.api.comum.eventos.ServicoCanceladoEvent;
import com.limio.api.comum.eventos.enums.TipoCancelamento;
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
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoRepository;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.sessao.SessaoRepository;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatService;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensal;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller → banco real) do UC42 "Empregador cancelar após combinação" (ETI-94). O "agora" é um
 * {@link Clock} controlado, para fixar o limite exato de 24h e a carência. Serviço combinado, reagendamento aceito e
 * cota entram por repositório/SQL: nenhum fluxo cria proposta ainda (ETI-99/ETI-100). O plano é um {@link
 * MockitoBean}: o {@code financeiro} ainda é stub e sempre devolveria FREE. As corridas usam threads reais e conferem
 * o ESTADO FINAL do banco (um só cancelamento, uma só devolução de cota), não só os códigos HTTP.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class CancelarServicoCombinadoStoryTest {

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
    private ReagendamentoRepository reagendamentoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents eventos;

    @MockitoBean
    private Clock relogio;

    @MockitoBean
    private ConsultaPlanoPrestador consultaPlanoPrestador;

    @MockitoSpyBean
    private AplicacaoService aplicacaoService;

    @MockitoSpyBean
    private ChatService chatService;

    private Instant agora;

    @BeforeEach
    void limparBaseEFixarORelogio() {
        limparBase();
        agora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        when(relogio.instant()).thenReturn(agora);
        when(relogio.getZone()).thenReturn(ZoneOffset.UTC);
        when(consultaPlanoPrestador.planoAtual(any())).thenReturn(Plano.FREE);
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private void limparBase() {
        jdbc.execute("delete from log_auditoria");
        jdbc.execute("delete from mensagem");
        jdbc.execute("delete from chat");
        jdbc.execute("delete from cota_mensal");
        jdbc.execute("delete from perfil_prestador");
        reagendamentoRepository.deleteAll();
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
        sessaoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // ---------------------------------------------------------------- cenário

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

    private Anuncio anuncio(UUID contratanteId, StatusAnuncio status, Duration ateADataVigente) {
        UUID categoriaId = jdbc.queryForObject("select id from categoria where protegida", UUID.class);
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(contratanteId)
                .titulo("Instalar 3 tomadas")
                .categoriaId(categoriaId)
                .descricao("Instalar 3 tomadas novas na sala e trocar o disjuntor")
                .dataHoraPretendida(agora.plus(ateADataVigente))
                .bairroRegiaoSnapshot("Pinheiros")
                .enderecoCompletoSnapshot("Rua dos Pinheiros, 100, Pinheiros, São Paulo - SP")
                .status(status)
                .build());
    }

    private Aplicacao escolhida(UUID anuncioId, UUID prestadorId, boolean cotaConsumida) {
        return aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(prestadorId)
                .status(StatusAplicacao.ESCOLHIDA)
                .dataAplicacao(agora.minus(Duration.ofDays(1)))
                .escolhidaEm(agora)
                .cotaConsumida(cotaConsumida)
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

    /** Prestador com perfil e {@code consumidas} escolhas gastas no mês da escolha (a escolha é feita em {@code agora}). */
    private Usuario prestador(String nome, String email, String cpf, int consumidas) {
        Usuario prestador = usuario(nome, email, cpf, PapelUsuario.PRESTADOR);
        UUID perfilId = perfil(prestador.getId());
        if (consumidas > 0) {
            jdbc.update("insert into cota_mensal (perfil_prestador_id, ano_mes, escolhas_consumidas, teto_aplicado) "
                    + "values (?, ?, ?, ?)", perfilId, CotaMensal.mesDe(agora).toString(), consumidas, TETO);
        }
        return prestador;
    }

    private int consumidas(Usuario prestador) {
        List<Integer> linhas = jdbc.queryForList("""
                select c.escolhas_consumidas from cota_mensal c
                join perfil_prestador p on p.id = c.perfil_prestador_id
                where p.usuario_id = ? and c.ano_mes = ?""", Integer.class, prestador.getId(),
                CotaMensal.mesDe(agora).toString());
        return linhas.isEmpty() ? 0 : linhas.get(0);
    }

    private Reagendamento reagendamento(UUID anuncioId, StatusReagendamento status, Instant respondidoEm) {
        return reagendamentoRepository.save(Reagendamento.builder()
                .anuncioId(anuncioId)
                .dataAnterior(agora.plus(Duration.ofDays(3)))
                .dataProposta(agora.plus(Duration.ofDays(5)))
                .status(status)
                .respondidoEm(respondidoEm)
                .build());
    }

    private UUID chat(Aplicacao aplicacao, UUID contratanteId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into chat (id, aplicacao_id, anuncio_id, contratante_id, prestador_id) "
                + "values (?, ?, ?, ?, ?)", id, aplicacao.getId(), aplicacao.getAnuncioId(), contratanteId,
                aplicacao.getPrestadorId());
        return id;
    }

    private ResultActions cancelar(String token, UUID anuncioId) throws Exception {
        return mockMvc.perform(post("/anuncios/{anuncioId}/servico/cancelar", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private int cancelarCodigo(String token, UUID anuncioId) throws Exception {
        return cancelar(token, anuncioId).andReturn().getResponse().getStatus();
    }

    private Anuncio recarregar(Anuncio anuncio) {
        return anuncioRepository.findById(anuncio.getId()).orElseThrow();
    }

    private Aplicacao recarregar(Aplicacao aplicacao) {
        return aplicacaoRepository.findById(aplicacao.getId()).orElseThrow();
    }

    private long auditorias() {
        return jdbc.queryForObject("select count(*) from log_auditoria", Long.class);
    }

    private void verificarNadaMudou(Anuncio anuncio, Aplicacao escolhida, Usuario prestador, int cotaEsperada) {
        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(recarregar(anuncio).getTipoCancelamento()).isNull();
        assertThat(recarregar(escolhida).getStatus()).isEqualTo(StatusAplicacao.ESCOLHIDA);
        assertThat(consumidas(prestador)).isEqualTo(cotaEsperada);
        assertThat(eventos.stream(ServicoCanceladoEvent.class).count()).isZero();
        assertThat(auditorias()).isZero();
    }

    // ---------------------------------------------------------------- caminho feliz

    @Test
    void dadoCombinadoComTresDiasEPrestadorFree_quandoEmpregadorCancela_entao200EncerraEstornaFechaOChatEMantemOContato()
            throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);
        UUID chatId = chat(doRafael, maria.getId());

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anuncioId", is(anuncio.getId().toString())))
                .andExpect(jsonPath("$.status", is("ENCERRADO")))
                .andExpect(jsonPath("$.tipoCancelamento", is("NORMAL")))
                .andExpect(jsonPath("$.cotaEstornada", is(true)));

        Anuncio encerrado = recarregar(anuncio);
        assertThat(encerrado.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(encerrado.getTipoCancelamento()).isEqualTo(TipoCancelamento.NORMAL);
        assertThat(encerrado.getEncerradoEm()).isEqualTo(agora);
        Aplicacao encerrada = recarregar(doRafael);
        assertThat(encerrada.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(encerrada.getCotaEstornadaEm()).isEqualTo(agora);
        assertThat(consumidas(rafael)).isEqualTo(2);
        assertThat(aplicacaoService.contarEscolhidasNoPeriodo(rafael.getId(), agora.minus(Duration.ofDays(40)),
                agora.plus(Duration.ofDays(40)))).as("a barreira de candidatura devolve a vaga").isZero();
        assertThat(auditorias()).isZero();

        List<ServicoCanceladoEvent> publicados = eventos.stream(ServicoCanceladoEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).candidaturaEscolhidaId()).isEqualTo(doRafael.getId());
        assertThat(publicados.get(0).prestadorId()).isEqualTo(rafael.getId());
        assertThat(publicados.get(0).tipo()).isEqualTo(TipoCancelamento.NORMAL);
        assertThat(publicados.get(0).cotaEstornada()).isTrue();

        assertThat(jdbc.queryForObject("select somente_leitura from chat where id = ?", Boolean.class, chatId))
                .isTrue();
        assertThat(jdbc.queryForObject("select motivo_somente_leitura from chat where id = ?", String.class, chatId))
                .isEqualTo("SERVICO_CANCELADO");

        // o contato já liberado continua no histórico do Prestador escolhido
        mockMvc.perform(get("/anuncios/{id}/servico", anuncio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar("rafael@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ENCERRADO")))
                .andExpect(jsonPath("$.outraParte.telefone", is("+5511988887777")));
    }

    // ---------------------------------------------------------------- antecedência de 24h

    @Test
    void dadoMenosDe24Horas_quandoCancela_entao409PrazoEsgotadoENadaMuda() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofHours(24).minusSeconds(1));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("PRAZO_CANCELAMENTO_ESGOTADO")));

        verificarNadaMudou(anuncio, doRafael, rafael, 3);
    }

    @Test
    void dadoExatamente24Horas_quandoCancela_entao200() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofHours(24));
        escolhida(anuncio.getId(), rafael.getId(), true);

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoCancelamento", is("NORMAL")))
                .andExpect(jsonPath("$.cotaEstornada", is(true)));

        assertThat(consumidas(rafael)).isEqualTo(2);
    }

    // ---------------------------------------------------------------- carência pós-reagendamento

    @Test
    void dadoReagendamentoAceitoDentroDaCarencia_quandoCancela_entaoTardioSemEstornoEComAuditoria() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(5));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);
        Reagendamento aceito = reagendamento(anuncio.getId(), StatusReagendamento.ACEITO,
                agora.minus(Duration.ofHours(2)));

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoCancelamento", is("TARDIO")))
                .andExpect(jsonPath("$.cotaEstornada", is(false)));

        assertThat(recarregar(anuncio).getTipoCancelamento()).isEqualTo(TipoCancelamento.TARDIO);
        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(recarregar(doRafael).getCotaEstornadaEm()).isNull();
        assertThat(consumidas(rafael)).isEqualTo(3);
        assertThat(aplicacaoService.contarEscolhidasNoPeriodo(rafael.getId(), agora.minus(Duration.ofDays(40)),
                agora.plus(Duration.ofDays(40)))).as("sem estorno, a escolha segue contando").isEqualTo(1);

        assertThat(auditorias()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select tipo_acao from log_auditoria", String.class))
                .isEqualTo("COMBINADO_CANCELADO_TARDIO");
        assertThat(jdbc.queryForObject("select entidade_id from log_auditoria", UUID.class))
                .isEqualTo(anuncio.getId());
        String detalhes = jdbc.queryForObject("select detalhes_json from log_auditoria", String.class);
        assertThat(objectMapper.readTree(detalhes).get("dataAnterior").asString())
                .isEqualTo(aceito.getDataAnterior().toString());
        assertThat(objectMapper.readTree(detalhes).get("dataNova").asString())
                .isEqualTo(aceito.getDataProposta().toString());

        assertThat(eventos.stream(ServicoCanceladoEvent.class).toList()).singleElement()
                .satisfies(evento -> assertThat(evento.tipo()).isEqualTo(TipoCancelamento.TARDIO));
    }

    @Test
    void dadoReagendamentoAceitoForaDaCarencia_quandoCancela_entaoNormalComEstorno() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(5));
        escolhida(anuncio.getId(), rafael.getId(), true);
        reagendamento(anuncio.getId(), StatusReagendamento.ACEITO, agora.minus(Duration.ofHours(25)));

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoCancelamento", is("NORMAL")))
                .andExpect(jsonPath("$.cotaEstornada", is(true)));

        assertThat(consumidas(rafael)).isEqualTo(2);
        assertThat(auditorias()).isZero();
    }

    @Test
    void dadaCarenciaMasMenosDe24HorasAteADataVigente_quandoCancela_entao409() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofHours(10));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);
        reagendamento(anuncio.getId(), StatusReagendamento.ACEITO, agora.minus(Duration.ofHours(1)));

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("PRAZO_CANCELAMENTO_ESGOTADO")));

        verificarNadaMudou(anuncio, doRafael, rafael, 3);
    }

    // ---------------------------------------------------------------- plano na escolha

    @Test
    void dadoPrestadorGoldNaEscolha_quandoCancela_entao200SemEstornarCota() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        escolhida(anuncio.getId(), rafael.getId(), false);

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoCancelamento", is("NORMAL")))
                .andExpect(jsonPath("$.cotaEstornada", is(false)));

        assertThat(consumidas(rafael)).isEqualTo(3);
    }

    @Test
    void dadoPrestadorFreeNaEscolhaQueVirouGold_quandoCancela_entaoEstornaPeloHistoricoDaEscolha() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        when(consultaPlanoPrestador.planoAtual(rafael.getId())).thenReturn(Plano.GOLD);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        escolhida(anuncio.getId(), rafael.getId(), true);

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cotaEstornada", is(true)));

        assertThat(consumidas(rafael)).isEqualTo(2);
    }

    @Test
    void dadaEscolhaDeOutroMes_quandoCancela_entaoEstornaNaCotaDoMesDaEscolha() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
        UUID perfilId = perfil(rafael.getId());
        String mesDaEscolha = CotaMensal.mesDe(agora).minusMonths(1).toString();
        String mesAtual = CotaMensal.mesDe(agora).toString();
        jdbc.update("insert into cota_mensal (perfil_prestador_id, ano_mes, escolhas_consumidas, teto_aplicado) "
                + "values (?, ?, 3, ?), (?, ?, 1, ?)", perfilId, mesDaEscolha, TETO, perfilId, mesAtual, TETO);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        Aplicacao doRafael = aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(rafael.getId())
                .status(StatusAplicacao.ESCOLHIDA)
                .dataAplicacao(agora.minus(Duration.ofDays(40)))
                .escolhidaEm(CotaMensal.zerada(perfilId, CotaMensal.mesDe(agora).minusMonths(1), TETO).inicio()
                        .plus(Duration.ofDays(2)))
                .cotaConsumida(true)
                .build());

        cancelar(logar("maria@example.com"), anuncio.getId()).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select escolhas_consumidas from cota_mensal where ano_mes = ?",
                Integer.class, mesDaEscolha)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select escolhas_consumidas from cota_mensal where ano_mes = ?",
                Integer.class, mesAtual)).as("o mês atual não é tocado").isEqualTo(1);
        assertThat(recarregar(doRafael).getCotaEstornadaEm()).isEqualTo(agora);
    }

    // ---------------------------------------------------------------- proposta pendente

    @Test
    void dadaPropostaDeReagendamentoPendente_quandoCancela_entaoAPropostaPerdeOEfeito() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        escolhida(anuncio.getId(), rafael.getId(), true);
        Reagendamento pendente = reagendamento(anuncio.getId(), StatusReagendamento.PENDENTE, null);

        cancelar(logar("maria@example.com"), anuncio.getId()).andExpect(status().isOk());

        Reagendamento depois = reagendamentoRepository.findById(pendente.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
        assertThat(depois.getRespondidoEm()).isEqualTo(agora);
    }

    // ---------------------------------------------------------------- estados que recusam

    @Test
    void dadoServicoEmAndamento_quandoCancela_entao409UseDenuncia() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.EM_ANDAMENTO, Duration.ofDays(3));
        escolhida(anuncio.getId(), rafael.getId(), true);

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("USE_DENUNCIA")));

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.EM_ANDAMENTO);
        assertThat(consumidas(rafael)).isEqualTo(3);
        assertThat(eventos.stream(ServicoCanceladoEvent.class).count()).isZero();
    }

    @Test
    void dadoServicoAguardandoValidacao_quandoCancela_entao409UseDenuncia() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.AGUARDANDO_VALIDACAO, Duration.ofDays(3));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);
        doRafael.setStatus(StatusAplicacao.AGUARDANDO_VALIDACAO);
        aplicacaoRepository.save(doRafael);

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("USE_DENUNCIA")));

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.AGUARDANDO_VALIDACAO);
        assertThat(recarregar(doRafael).getStatus()).isEqualTo(StatusAplicacao.AGUARDANDO_VALIDACAO);
        assertThat(consumidas(rafael)).isEqualTo(3);
    }

    @Test
    void dadoPublicadoSemEscolhido_quandoCancela_entao409EstadoInvalido() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
    }

    // ---------------------------------------------------------------- autorização

    @Test
    void dadoEmpregadorQueNaoEDono_quandoCancela_entao404ENadaMuda() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        usuario("Carlos Dias", "carlos@example.com", "11144477735", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);

        cancelar(logar("carlos@example.com"), anuncio.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));

        verificarNadaMudou(anuncio, doRafael, rafael, 3);
    }

    @Test
    void dadoPrestadorEscolhido_quandoTentaCancelarPorAqui_entao404() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);

        cancelar(logar("rafael@example.com"), anuncio.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));

        verificarNadaMudou(anuncio, doRafael, rafael, 3);
    }

    @Test
    void dadoServicoInexistente_quandoCancela_entao404() throws Exception {
        usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);

        cancelar(logar("maria@example.com"), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoSemToken_quandoCancela_entao401() throws Exception {
        mockMvc.perform(post("/anuncios/{anuncioId}/servico/cancelar", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- repetição e concorrência

    @Test
    void dadoCancelamentoRepetido_quandoCancelaDeNovo_entao409SemSegundoEstornoNemSegundoEvento() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        escolhida(anuncio.getId(), rafael.getId(), true);
        String token = logar("maria@example.com");

        cancelar(token, anuncio.getId()).andExpect(status().isOk());
        cancelar(token, anuncio.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));

        assertThat(consumidas(rafael)).isEqualTo(2);
        assertThat(eventos.stream(ServicoCanceladoEvent.class).count()).isEqualTo(1);
    }

    /** Clique duplo: dois cancelamentos disparados juntos. Um vence (200), o outro recebe 409, e a cota desce UMA vez. */
    @Test
    void dadosDoisCancelamentosSimultaneos_entaoUmVenceAOutroRecebe409ECotaDesceUmaVez() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);
        String token = logar("maria@example.com");
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = executor.submit(() -> {
                largada.await();
                return cancelarCodigo(token, anuncio.getId());
            });
            Future<Integer> b = executor.submit(() -> {
                largada.await();
                return cancelarCodigo(token, anuncio.getId());
            });
            largada.countDown();

            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            largada.countDown();
            executor.shutdownNow();
        }

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(recarregar(doRafael).getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(consumidas(rafael)).isEqualTo(2);
        assertThat(eventos.stream(ServicoCanceladoEvent.class).count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- falhas

    /** O estorno acontece na mesma transação do encerramento: se a gravação final falha, nada fica pela metade. */
    @Test
    void dadaFalhaAoGravarACandidaturaDepoisDoEstorno_entaoRollbackTotal() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);
        UUID chatId = chat(doRafael, maria.getId());
        doThrow(new IllegalStateException("falha simulada")).when(aplicacaoService).salvar(any(Aplicacao.class));

        cancelar(logar("maria@example.com"), anuncio.getId()).andExpect(status().isInternalServerError());

        verificarNadaMudou(anuncio, doRafael, rafael, 3);
        assertThat(recarregar(doRafael).getCotaEstornadaEm()).isNull();
        assertThat(jdbc.queryForObject("select somente_leitura from chat where id = ?", Boolean.class, chatId))
                .as("sem commit, o chat não reage").isFalse();
    }

    /** O chat reage depois do commit (ADR-0003): se falha, é logado e o cancelamento já confirmado permanece. */
    @Test
    void dadoCancelamentoConfirmado_quandoOChatFalhaDepoisDoCommit_entaoOCancelamentoPermanece() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);
        UUID chatId = chat(doRafael, maria.getId());
        doThrow(new IllegalStateException("falha simulada")).when(chatService).salvar(any(Chat.class));

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anuncioId", is(anuncio.getId().toString())));

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(recarregar(doRafael).getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(consumidas(rafael)).isEqualTo(2);
        assertThat(eventos.stream(ServicoCanceladoEvent.class).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select somente_leitura from chat where id = ?", Boolean.class, chatId))
                .as("a reação do chat falhou e não volta para quem cancelou").isFalse();
    }
}
