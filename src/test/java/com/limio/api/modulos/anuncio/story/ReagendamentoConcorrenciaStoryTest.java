package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.ReagendamentoAceitoEvent;
import com.limio.api.comum.eventos.ReagendamentoCanceladoEvent;
import com.limio.api.comum.eventos.ReagendamentoExpiradoEvent;
import com.limio.api.comum.eventos.ReagendamentoRecusadoEvent;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.actions.usecase.ExpirarReagendamentosUseCase;
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

import tools.jackson.databind.ObjectMapper;

/**
 * Concorrência do reagendamento (ETI-99, cenário 37), contra Postgres real e com threads reais: aceitar, recusar,
 * cancelar e expirar a MESMA proposta ao mesmo tempo, e as corridas dela com o serviço (cancelar, iniciar). Cada
 * corrida se repete algumas vezes, com propostas novas, para aumentar a chance de o escalonamento variar. As
 * asserções olham o ESTADO FINAL do banco (um único desfecho, uma única auditoria, um único evento, data vigente
 * coerente), não só os códigos HTTP. O relógio é um {@link MockitoBean} por thread: as chamadas HTTP veem "antes" do
 * prazo da proposta e o job vê "depois", o que permite disputar a expiração com as respostas.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class ReagendamentoConcorrenciaStoryTest {

    private static final String SENHA = "senhaForte123";
    private static final String JUSTIFICATIVA = "Imprevisto de agenda";

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

    /** "Agora" da thread que roda a tarefa; sem valor, vale o {@code agora} do cenário. */
    private final ThreadLocal<Instant> relogioDaThread = new ThreadLocal<>();

    @Autowired
    private ExpirarReagendamentosUseCase expirarReagendamentos;

    private Instant agora;
    private Usuario maria;
    private Usuario rafael;

    @BeforeEach
    void limparBaseEFixarORelogio() {
        limparBase();
        agora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        relogioDaThread.remove();
        when(relogio.instant()).thenAnswer(invocacao -> relogioDaThread.get() != null ? relogioDaThread.get() : agora);
        when(relogio.getZone()).thenReturn(ZoneOffset.UTC);
        maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        rafael = usuario("Rafael Souza", "rafael@example.com", "39053344705", PapelUsuario.PRESTADOR);
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private void limparBase() {
        jdbc.execute("delete from log_auditoria");
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

    private Anuncio anuncio(StatusAnuncio status, Duration ateADataVigente) {
        UUID categoriaId = jdbc.queryForObject("select id from categoria where protegida", UUID.class);
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(maria.getId())
                .titulo("Instalar 3 tomadas")
                .categoriaId(categoriaId)
                .descricao("Instalar 3 tomadas novas na sala e trocar o disjuntor")
                .dataHoraPretendida(agora.plus(ateADataVigente))
                .bairroRegiaoSnapshot("Pinheiros")
                .enderecoCompletoSnapshot("Rua dos Pinheiros, 100, Pinheiros, São Paulo - SP")
                .status(status)
                .build());
    }

    private Anuncio combinado(Duration ateADataVigente) {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, ateADataVigente);
        aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ESCOLHIDA);
        return anuncio;
    }

    private Aplicacao aplicacao(UUID anuncioId, UUID prestadorId, StatusAplicacao status) {
        return aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(prestadorId)
                .status(status)
                .dataAplicacao(agora.minus(Duration.ofDays(1)))
                .escolhidaEm(status == StatusAplicacao.ENVIADA ? null : agora.minus(Duration.ofHours(12)))
                .build());
    }

    /** Proposta anterior já encerrada (ou pendente) do anúncio, gravada direto no banco. */
    private Reagendamento proposta(Anuncio anuncio, UUID proponenteId, StatusReagendamento status, Instant expiraEm) {
        return reagendamentoRepository.save(Reagendamento.builder()
                .anuncioId(anuncio.getId())
                .dataAnterior(anuncio.getDataHoraPretendida())
                .dataProposta(anuncio.getDataHoraPretendida().plus(Duration.ofDays(2)))
                .justificativa(JUSTIFICATIVA)
                .status(status)
                .respondidoEm(status == StatusReagendamento.PENDENTE ? null : agora.minus(Duration.ofHours(1)))
                .proponenteId(proponenteId)
                .expiraEm(expiraEm)
                .build());
    }

    private Reagendamento pendenteDe(Anuncio anuncio, UUID proponenteId) {
        return proposta(anuncio, proponenteId, StatusReagendamento.PENDENTE, agora.plus(Duration.ofHours(24)));
    }

    private ResultActions responder(String token, UUID reagendamentoId, String corpo) throws Exception {
        return mockMvc.perform(post("/reagendamentos/{id}/responder", reagendamentoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions aceitar(String token, UUID reagendamentoId) throws Exception {
        return responder(token, reagendamentoId, "{\"decisao\":\"ACEITAR\"}");
    }

    private ResultActions recusar(String token, UUID reagendamentoId) throws Exception {
        return responder(token, reagendamentoId, "{\"decisao\":\"RECUSAR\"}");
    }

    private int codigo(ResultActions acao) throws Exception {
        return acao.andReturn().getResponse().getStatus();
    }

    private Anuncio recarregar(Anuncio anuncio) {
        return anuncioRepository.findById(anuncio.getId()).orElseThrow();
    }

    private Aplicacao recarregar(Aplicacao aplicacao) {
        return aplicacaoRepository.findById(aplicacao.getId()).orElseThrow();
    }

    private Reagendamento recarregar(Reagendamento reagendamento) {
        return reagendamentoRepository.findById(reagendamento.getId()).orElseThrow();
    }

    private long auditorias(String tipoAcao) {
        return jdbc.queryForObject("select count(*) from log_auditoria where tipo_acao = ?", Long.class, tipoAcao);
    }

    private long eventosDeResposta() {
        return eventos.stream(ReagendamentoAceitoEvent.class).count()
                + eventos.stream(ReagendamentoRecusadoEvent.class).count();
    }

    /** Nada mudou: a proposta segue como estava, a data vigente é a de antes e nada foi auditado nem publicado. */
    private void verificarNadaMudou(Anuncio anuncio, Reagendamento proposta, StatusReagendamento statusEsperado) {
        assertThat(recarregar(proposta).getStatus()).isEqualTo(statusEsperado);
        assertThat(recarregar(proposta).getRespondidoEm()).isEqualTo(proposta.getRespondidoEm());
        assertThat(recarregar(anuncio).getDataHoraPretendida()).isEqualTo(anuncio.getDataHoraPretendida());
        assertThat(auditorias("REAGENDAMENTO_ACEITO") + auditorias("REAGENDAMENTO_RECUSADO")).isZero();
        assertThat(eventosDeResposta()).isZero();
    }

    // ---------------------------------------------------------------- helpers das corridas

    private static final int RODADAS = 6;

    private ResultActions cancelarProposta(String token, UUID reagendamentoId) throws Exception {
        return mockMvc.perform(post("/reagendamentos/{id}/cancelar", reagendamentoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions cancelarServico(String token, UUID anuncioId) throws Exception {
        return mockMvc.perform(post("/anuncios/{id}/servico/cancelar", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions iniciar(String token, UUID anuncioId) throws Exception {
        return mockMvc.perform(post("/anuncios/{id}/iniciar", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions propor(String token, UUID anuncioId) throws Exception {
        return mockMvc.perform(post("/anuncios/{id}/reagendamentos", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"novaDataHora\":\"" + agora.plus(Duration.ofDays(8)) + "\",\"justificativa\":\"Outra data\"}"));
    }

    /** Roda a tarefa vendo "agora" como {@code instante} (o relógio é por thread). */
    private Callable<Integer> vendo(Instant instante, Callable<Integer> tarefa) {
        return () -> {
            relogioDaThread.set(instante);
            try {
                return tarefa.call();
            } finally {
                relogioDaThread.remove();
            }
        };
    }

    /** Todas as tarefas largam juntas; devolve o resultado de cada uma, na ordem recebida. */
    private static List<Integer> disputar(List<Callable<Integer>> tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Integer>> futuros = tarefas.stream().map(tarefa -> pool.submit(() -> {
                largada.await();
                return tarefa.call();
            })).toList();
            largada.countDown();
            List<Integer> resultados = new java.util.ArrayList<>();
            for (Future<Integer> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            largada.countDown();
            pool.shutdownNow();
        }
    }

    private long auditoriasDaProposta(Reagendamento proposta, String... tipos) {
        long total = 0;
        for (String tipo : tipos) {
            total += jdbc.queryForObject("select count(*) from log_auditoria where entidade_id = ? and tipo_acao = ?",
                    Long.class, proposta.getId(), tipo);
        }
        return total;
    }

    private long eventosDaProposta(Reagendamento proposta) {
        UUID id = proposta.getId();
        return eventos.stream(ReagendamentoAceitoEvent.class).filter(e -> e.reagendamentoId().equals(id)).count()
                + eventos.stream(ReagendamentoRecusadoEvent.class).filter(e -> e.reagendamentoId().equals(id)).count()
                + eventos.stream(ReagendamentoCanceladoEvent.class).filter(e -> e.reagendamentoId().equals(id)).count()
                + eventos.stream(ReagendamentoExpiradoEvent.class).filter(e -> e.reagendamentoId().equals(id)).count();
    }

    // ---------------------------------------------------------------- aceitar x recusar x cancelar x expirar

    @Test
    void dadosAceiteRecusaCancelamentoEExpiracaoSimultaneos_entaoSoUmEncerraAPropostaEOEstadoFicaConsistente()
            throws Exception {
        String tokenRafael = logar("rafael@example.com");
        String tokenMaria = logar("maria@example.com");

        for (int rodada = 0; rodada < RODADAS; rodada++) {
            Anuncio anuncio = combinado(Duration.ofDays(3));
            Instant vigenteOriginal = recarregar(anuncio).getDataHoraPretendida();
            // proposta da Maria: o Prestador pode aceitar/recusar, a própria Maria pode cancelar, o job pode expirar
            Reagendamento proposta = proposta(anuncio, maria.getId(), StatusReagendamento.PENDENTE,
                    agora.plus(Duration.ofHours(1)));
            Instant antesDoPrazo = agora;
            Instant depoisDoPrazo = agora.plus(Duration.ofHours(2));

            List<Integer> codigos = disputar(List.of(
                    vendo(antesDoPrazo, () -> codigo(aceitar(tokenRafael, proposta.getId()))),
                    vendo(antesDoPrazo, () -> codigo(recusar(tokenRafael, proposta.getId()))),
                    vendo(antesDoPrazo, () -> codigo(cancelarProposta(tokenMaria, proposta.getId()))),
                    vendo(depoisDoPrazo, () -> {
                        expirarReagendamentos.executar();
                        return 0;
                    })));

            StatusReagendamento fim = recarregar(proposta).getStatus();
            assertThat(fim).as("rodada %s", rodada).isIn(StatusReagendamento.ACEITO, StatusReagendamento.RECUSADO,
                    StatusReagendamento.CANCELADO, StatusReagendamento.EXPIRADO);
            // cada resposta HTTP é 200 (venceu) ou 409 (já encerrada); só uma pode vencer, e nenhuma se o job venceu
            int aceite = codigos.get(0);
            int recusa = codigos.get(1);
            int cancelamento = codigos.get(2);
            assertThat(List.of(aceite, recusa, cancelamento)).as("rodada %s: códigos", rodada)
                    .allMatch(codigo -> codigo == 200 || codigo == 409);
            assertThat(aceite == 200).isEqualTo(fim == StatusReagendamento.ACEITO);
            assertThat(recusa == 200).isEqualTo(fim == StatusReagendamento.RECUSADO);
            assertThat(cancelamento == 200).isEqualTo(fim == StatusReagendamento.CANCELADO);
            // um único desfecho: uma auditoria e um evento, do tipo que venceu
            assertThat(auditoriasDaProposta(proposta, "REAGENDAMENTO_ACEITO", "REAGENDAMENTO_RECUSADO",
                    "REAGENDAMENTO_CANCELADO", "REAGENDAMENTO_EXPIRADO")).as("rodada %s: auditorias", rodada)
                    .isEqualTo(1);
            assertThat(auditoriasDaProposta(proposta, "REAGENDAMENTO_" + fim)).isEqualTo(1);
            assertThat(eventosDaProposta(proposta)).as("rodada %s: eventos", rodada).isEqualTo(1);
            // a data vigente só muda se, e somente se, o aceite venceu
            assertThat(recarregar(anuncio).getDataHoraPretendida())
                    .isEqualTo(fim == StatusReagendamento.ACEITO ? proposta.getDataProposta() : vigenteOriginal);
            assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        }
    }

    // ---------------------------------------------------------------- aceitar x cancelar o serviço

    @Test
    void dadosAceiteECancelamentoDoServicoSimultaneos_entaoOServicoEncerraEAPropostaFicaAceitaOuSemEfeito()
            throws Exception {
        String tokenRafael = logar("rafael@example.com");
        String tokenMaria = logar("maria@example.com");

        for (int rodada = 0; rodada < RODADAS; rodada++) {
            Anuncio anuncio = combinado(Duration.ofDays(3));
            Instant vigenteOriginal = recarregar(anuncio).getDataHoraPretendida();
            Reagendamento proposta = pendenteDe(anuncio, maria.getId());

            List<Integer> codigos = disputar(List.of(
                    () -> codigo(aceitar(tokenRafael, proposta.getId())),
                    () -> codigo(cancelarServico(tokenMaria, anuncio.getId()))));

            // o Empregador sempre consegue cancelar (a nova data também dá mais de 24h)
            assertThat(codigos.get(1)).as("rodada %s: cancelar o serviço", rodada).isEqualTo(200);
            assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
            StatusReagendamento fim = recarregar(proposta).getStatus();
            assertThat(fim).isIn(StatusReagendamento.ACEITO, StatusReagendamento.SEM_EFEITO);
            long tardios = jdbc.queryForObject("select count(*) from log_auditoria where tipo_acao = "
                    + "'COMBINADO_CANCELADO_TARDIO' and entidade_id = ?", Long.class, anuncio.getId());
            if (fim == StatusReagendamento.ACEITO) {
                // aceite primeiro: o cancelamento caiu na carência pós-reagendamento (RB36), sobre a nova data
                assertThat(codigos.get(0)).isEqualTo(200);
                assertThat(recarregar(anuncio).getDataHoraPretendida()).isEqualTo(proposta.getDataProposta());
                assertThat(tardios).as("rodada %s: cancelamento tardio", rodada).isEqualTo(1);
            } else {
                // cancelamento primeiro: a proposta perdeu o efeito e o aceite recebeu 409
                assertThat(codigos.get(0)).isEqualTo(409);
                assertThat(recarregar(anuncio).getDataHoraPretendida()).isEqualTo(vigenteOriginal);
                assertThat(tardios).isZero();
                assertThat(auditoriasDaProposta(proposta, "REAGENDAMENTO_ACEITO")).isZero();
            }
        }
    }

    // ---------------------------------------------------------------- propor x iniciar o serviço

    @Test
    void dadasPropostaEInicioDoServicoSimultaneos_entaoNenhumaPropostaPendenteSobraNoServicoEmAndamento()
            throws Exception {
        String tokenRafael = logar("rafael@example.com");
        String tokenMaria = logar("maria@example.com");

        for (int rodada = 0; rodada < RODADAS; rodada++) {
            Anuncio anuncio = combinado(Duration.ofDays(3));

            List<Integer> codigos = disputar(List.of(
                    () -> codigo(propor(tokenMaria, anuncio.getId())),
                    () -> codigo(iniciar(tokenRafael, anuncio.getId()))));

            // o serviço sempre inicia; a proposta ou perdeu a corrida (409) ou nasceu e foi encerrada sem efeito
            assertThat(codigos.get(1)).as("rodada %s: iniciar", rodada).isEqualTo(200);
            assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.EM_ANDAMENTO);
            List<Reagendamento> propostas = reagendamentoRepository.findAll().stream()
                    .filter(r -> r.getAnuncioId().equals(anuncio.getId())).toList();
            assertThat(propostas).noneMatch(r -> r.getStatus() == StatusReagendamento.PENDENTE);
            if (codigos.get(0) == 200) {
                assertThat(propostas).hasSize(1);
                assertThat(propostas.get(0).getStatus()).isEqualTo(StatusReagendamento.SEM_EFEITO);
            } else {
                assertThat(codigos.get(0)).isEqualTo(409);
                assertThat(propostas).isEmpty();
            }
        }
    }
}
