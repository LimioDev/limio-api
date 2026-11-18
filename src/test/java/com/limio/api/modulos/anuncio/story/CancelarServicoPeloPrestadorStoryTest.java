package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
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
import com.limio.api.comum.eventos.ServicoCanceladoPeloPrestadorEvent;
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
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensal;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller → banco real) do UC43 "Prestador cancelar após ser escolhido" (ETI-96). Serviço
 * combinado, proposta de reagendamento e cota entram por repositório/SQL: nenhum fluxo cria proposta ainda
 * (ETI-99/ETI-100). O "agora" é um {@link Clock} controlado e o plano um {@link MockitoBean} (o {@code financeiro} é
 * stub). O chat somente leitura é coberto na story do chat.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class CancelarServicoPeloPrestadorStoryTest {

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
        return mockMvc.perform(post("/anuncios/{anuncioId}/servico/cancelar-prestador", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
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

    private void verificarNadaMudou(Anuncio anuncio, Aplicacao escolhida, StatusAnuncio statusEsperado,
            StatusAplicacao statusDaEscolhidaEsperado) {
        assertThat(recarregar(anuncio).getStatus()).isEqualTo(statusEsperado);
        assertThat(recarregar(anuncio).getEncerradoEm()).isNull();
        assertThat(recarregar(escolhida).getStatus()).isEqualTo(statusDaEscolhidaEsperado);
        assertThat(eventos.stream(ServicoCanceladoPeloPrestadorEvent.class).count()).isZero();
    }

    // ---------------------------------------------------------------- caminho feliz

    @Test
    void dadoCombinadoEPrestadorFree_quandoEscolhidoCancela_entao200EncerraEMantemACotaConsumida() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofHours(2));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);

        cancelar(logar("rafael@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anuncioId", is(anuncio.getId().toString())))
                .andExpect(jsonPath("$.status", is("ENCERRADO")))
                .andExpect(jsonPath("$.encerradoEm").exists());

        Anuncio encerrado = recarregar(anuncio);
        assertThat(encerrado.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(encerrado.getEncerradoEm()).isEqualTo(agora);
        assertThat(encerrado.getTipoCancelamento()).isNull();
        Aplicacao encerrada = recarregar(doRafael);
        assertThat(encerrada.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(encerrada.getEncerradaEm()).isEqualTo(agora);
        assertThat(encerrada.getCotaEstornadaEm()).isNull();
        assertThat(consumidas(rafael)).as("RB18: a cota consumida continua consumida").isEqualTo(3);
        assertThat(aplicacaoService.contarEscolhidasNoPeriodo(rafael.getId(), agora.minus(Duration.ofDays(40)),
                agora.plus(Duration.ofDays(40)))).as("a escolha segue contando na barreira de candidatura (sem estorno)")
                .isEqualTo(1);

        List<ServicoCanceladoPeloPrestadorEvent> publicados = eventos
                .stream(ServicoCanceladoPeloPrestadorEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).candidaturaEscolhidaId()).isEqualTo(doRafael.getId());
        assertThat(publicados.get(0).contratanteId()).isEqualTo(maria.getId());
        assertThat(publicados.get(0).prestadorId()).isEqualTo(rafael.getId());
    }

    @Test
    void dadoServicoEmAndamento_quandoEscolhidoCancela_entao200Encerrado() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 2);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.EM_ANDAMENTO, Duration.ofHours(1));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);

        cancelar(logar("rafael@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ENCERRADO")));

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(recarregar(doRafael).getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(consumidas(rafael)).isEqualTo(2);
        assertThat(eventos.stream(ServicoCanceladoPeloPrestadorEvent.class).count()).isEqualTo(1);
    }

    @Test
    void dadaPropostaDeReagendamentoPendente_quandoEscolhidoCancela_entaoPropostaEncerradaSemEfeito()
            throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        escolhida(anuncio.getId(), rafael.getId(), true);
        Reagendamento proposta = reagendamento(anuncio.getId(), StatusReagendamento.PENDENTE, null);
        Instant dataVigente = recarregar(anuncio).getDataHoraPretendida();

        cancelar(logar("rafael@example.com"), anuncio.getId()).andExpect(status().isOk());

        Reagendamento encerrada = reagendamentoRepository.findById(proposta.getId()).orElseThrow();
        assertThat(encerrada.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
        assertThat(encerrada.getRespondidoEm()).isEqualTo(agora);
        assertThat(recarregar(anuncio).getDataHoraPretendida()).as("a proposta não vira a data vigente")
                .isEqualTo(dataVigente);
        assertThat(consumidas(rafael)).isEqualTo(3);
    }

    // ---------------------------------------------------------------- recusas

    @Test
    void dadoAguardandoValidacao_quandoEscolhidoCancela_entao409ENadaMuda() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.AGUARDANDO_VALIDACAO, Duration.ofHours(-1));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);
        doRafael.setStatus(StatusAplicacao.AGUARDANDO_VALIDACAO);
        aplicacaoRepository.save(doRafael);

        cancelar(logar("rafael@example.com"), anuncio.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));

        verificarNadaMudou(anuncio, doRafael, StatusAnuncio.AGUARDANDO_VALIDACAO, StatusAplicacao.AGUARDANDO_VALIDACAO);
        assertThat(consumidas(rafael)).isEqualTo(3);
    }

    @Test
    void dadoPrestadorNaoEscolhido_quandoCancela_entao404SERVICO_NAO_ENCONTRADOENadaMuda() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        prestador("Carlos Lima", "carlos@example.com", "11144477735", 1);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);

        cancelar(logar("carlos@example.com"), anuncio.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));

        verificarNadaMudou(anuncio, doRafael, StatusAnuncio.PUBLICADO, StatusAplicacao.ESCOLHIDA);
        assertThat(consumidas(rafael)).isEqualTo(3);
    }

    @Test
    void dadoServicoInexistente_quandoPrestadorCancela_entao404() throws Exception {
        prestador("Rafael Souza", "rafael@example.com", "39053344705", 0);

        cancelar(logar("rafael@example.com"), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoServicoJaEncerrado_quandoEscolhidoCancelaDeNovo_entao409SemSegundoEvento() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        escolhida(anuncio.getId(), rafael.getId(), true);
        String token = logar("rafael@example.com");

        cancelar(token, anuncio.getId()).andExpect(status().isOk());
        cancelar(token, anuncio.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));

        assertThat(eventos.stream(ServicoCanceladoPeloPrestadorEvent.class).count()).isEqualTo(1);
        assertThat(consumidas(rafael)).isEqualTo(3);
    }

    // ---------------------------------------------------------------- mural

    @Test
    void dadoServicoCancelado_quandoOutroPrestadorSeCandidata_entaoRecusadoEOAnuncioNaoVoltaAoMural()
            throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        prestador("Carlos Lima", "carlos@example.com", "11144477735", 0);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        escolhida(anuncio.getId(), rafael.getId(), true);
        cancelar(logar("rafael@example.com"), anuncio.getId()).andExpect(status().isOk());

        mockMvc.perform(post("/anuncios/{anuncioId}/candidaturas", anuncio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar("carlos@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_ESTADO_INVALIDO")));

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(aplicacaoRepository.findAll()).hasSize(1);
    }

    // ---------------------------------------------------------------- atomicidade

    /** Anúncio, candidatura e proposta mudam juntos: se a gravação final falha, nada fica pela metade. */
    @Test
    void dadaFalhaAoGravarACandidatura_entaoRollbackTotal() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "39053344705", 3);
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        Aplicacao doRafael = escolhida(anuncio.getId(), rafael.getId(), true);
        Reagendamento proposta = reagendamento(anuncio.getId(), StatusReagendamento.PENDENTE, null);
        doThrow(new IllegalStateException("falha simulada")).when(aplicacaoService).salvar(any(Aplicacao.class));

        cancelar(logar("rafael@example.com"), anuncio.getId()).andExpect(status().isInternalServerError());

        verificarNadaMudou(anuncio, doRafael, StatusAnuncio.PUBLICADO, StatusAplicacao.ESCOLHIDA);
        assertThat(reagendamentoRepository.findById(proposta.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusReagendamento.PENDENTE);
        assertThat(consumidas(rafael)).isEqualTo(3);
    }
}
