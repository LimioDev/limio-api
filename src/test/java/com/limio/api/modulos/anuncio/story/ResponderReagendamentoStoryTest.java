package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.ReagendamentoAceitoEvent;
import com.limio.api.comum.eventos.ReagendamentoRecusadoEvent;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
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
 * BDD ponta a ponta (controller → banco real) de "Aceitar ou recusar proposta de reagendamento" (ETI-99, US47). O
 * "agora" é um {@link Clock} controlado, para fixar o prazo de resposta e a carência pós-reagendamento. As propostas
 * entram por repositório; o cancelamento do serviço e do reagendamento usam os endpoints reais, para provar a
 * integração.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class ResponderReagendamentoStoryTest {

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

    private Instant agora;
    private Usuario maria;
    private Usuario rafael;

    @BeforeEach
    void limparBaseEFixarORelogio() {
        limparBase();
        agora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        when(relogio.instant()).thenReturn(agora);
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

    // ---------------------------------------------------------------- aceitar

    @Test
    void dadaPropostaDoEmpregador_quandoOPrestadorAceita_entao200AtualizaADataAuditaEPublicaEvento() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Aplicacao doRafael = aplicacaoRepository.findAll().get(0);
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());

        aceitar(logar("rafael@example.com"), proposta.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anuncioId", is(anuncio.getId().toString())))
                .andExpect(jsonPath("$.status", is("PUBLICADO")))
                .andExpect(jsonPath("$.dataHoraPretendida", is(proposta.getDataProposta().toString())))
                .andExpect(jsonPath("$.reagendamentosAceitos", is(1)))
                .andExpect(jsonPath("$.propostaPendente").doesNotExist());

        Reagendamento depois = recarregar(proposta);
        assertThat(depois.getStatus()).isEqualTo(StatusReagendamento.ACEITO);
        assertThat(depois.getRespondidoEm()).isEqualTo(agora);
        assertThat(recarregar(anuncio).getDataHoraPretendida()).isEqualTo(proposta.getDataProposta());
        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(recarregar(doRafael).getStatus()).isEqualTo(StatusAplicacao.ESCOLHIDA);
        assertThat(auditorias("REAGENDAMENTO_ACEITO")).isEqualTo(1);
        String detalhes = jdbc.queryForObject(
                "select detalhes_json from log_auditoria where tipo_acao = 'REAGENDAMENTO_ACEITO'", String.class);
        assertThat(detalhes).contains(anuncio.getDataHoraPretendida().toString())
                .contains(proposta.getDataProposta().toString());
        List<ReagendamentoAceitoEvent> publicados = eventos.stream(ReagendamentoAceitoEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).dataAnterior()).isEqualTo(anuncio.getDataHoraPretendida());
        assertThat(publicados.get(0).dataNova()).isEqualTo(proposta.getDataProposta());
        assertThat(publicados.get(0).proponenteId()).isEqualTo(maria.getId());
    }

    @Test
    void dadaPropostaDoPrestador_quandoOEmpregadorAceita_entao200() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, rafael.getId());

        aceitar(logar("maria@example.com"), proposta.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataHoraPretendida", is(proposta.getDataProposta().toString())))
                .andExpect(jsonPath("$.reagendamentosAceitos", is(1)));

        assertThat(recarregar(proposta).getStatus()).isEqualTo(StatusReagendamento.ACEITO);
    }

    @Test
    void dadaPropostaFeitaDepoisDaDataVigente_quandoAContraparteAceita_entaoAnovaDataFuturaPassaAVigorar()
            throws Exception {
        Anuncio anuncio = combinado(Duration.ofHours(-2));
        Reagendamento proposta = reagendamentoRepository.save(Reagendamento.builder()
                .anuncioId(anuncio.getId())
                .dataAnterior(anuncio.getDataHoraPretendida())
                .dataProposta(agora.plus(Duration.ofDays(2)))
                .justificativa(JUSTIFICATIVA)
                .status(StatusReagendamento.PENDENTE)
                .proponenteId(maria.getId())
                .expiraEm(agora.plus(Duration.ofHours(24)))
                .propostaAposDataVigente(true)
                .build());

        aceitar(logar("rafael@example.com"), proposta.getId()).andExpect(status().isOk());

        assertThat(recarregar(anuncio).getDataHoraPretendida()).isEqualTo(proposta.getDataProposta());
        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
    }

    @Test
    void dadoAceite_quandoOEmpregadorCancelaOServicoNaCarencia_entaoOPrazoDe24hUsaANovaDataECancelaComoTardio()
            throws Exception {
        // vigente em 3h: antes do aceite o Empregador não pode mais cancelar (menos de 24h de antecedência)
        Anuncio anuncio = combinado(Duration.ofHours(3));
        Reagendamento proposta = pendenteDe(anuncio, rafael.getId());
        String tokenMaria = logar("maria@example.com");

        mockMvc.perform(post("/anuncios/{id}/servico/cancelar", anuncio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenMaria))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("PRAZO_CANCELAMENTO_ESGOTADO")));

        aceitar(tokenMaria, proposta.getId()).andExpect(status().isOk());

        mockMvc.perform(post("/anuncios/{id}/servico/cancelar", anuncio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenMaria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoCancelamento", is("TARDIO")))
                .andExpect(jsonPath("$.cotaEstornada", is(false)));
        assertThat(auditorias("COMBINADO_CANCELADO_TARDIO")).isEqualTo(1);
    }

    @Test
    void dadaPropostaPropria_quandoOProponenteTentaAceitarOuRecusar_entao403SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());
        String token = logar("maria@example.com");

        aceitar(token, proposta.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
        recusar(token, proposta.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));

        verificarNadaMudou(anuncio, proposta, StatusReagendamento.PENDENTE);
    }

    @Test
    void dadaPropostaPendenteQuePassouDoPrazo_quandoAContraparteAceitaOuRecusa_entao409SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = proposta(anuncio, maria.getId(), StatusReagendamento.PENDENTE,
                agora.minus(Duration.ofMinutes(1)));
        String token = logar("rafael@example.com");

        aceitar(token, proposta.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("PROPOSTA_EXPIRADA")));
        recusar(token, proposta.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("PROPOSTA_EXPIRADA")));

        verificarNadaMudou(anuncio, proposta, StatusReagendamento.PENDENTE);
    }

    @Test
    void dadaPropostaJaEncerrada_quandoAContraparteAceitaOuRecusa_entao409SemEfeito() throws Exception {
        for (StatusReagendamento encerrado : List.of(StatusReagendamento.ACEITO, StatusReagendamento.RECUSADO,
                StatusReagendamento.CANCELADO, StatusReagendamento.EXPIRADO, StatusReagendamento.SEM_EFEITO)) {
            Anuncio anuncio = combinado(Duration.ofDays(3));
            Reagendamento proposta = proposta(anuncio, maria.getId(), encerrado, agora.plus(Duration.ofHours(24)));
            String token = logar("rafael@example.com");

            aceitar(token, proposta.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo", is("PROPOSTA_ENCERRADA")));
            recusar(token, proposta.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo", is("PROPOSTA_ENCERRADA")));

            verificarNadaMudou(anuncio, proposta, encerrado);
        }
    }

    @Test
    void dadaDataPropostaQueJaPassou_quandoAContraparteAceita_entao422SemEfeitoEPropostaSegueDePe() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());
        proposta.setDataProposta(agora.minus(Duration.ofMinutes(5)));
        reagendamentoRepository.save(proposta);

        aceitar(logar("rafael@example.com"), proposta.getId())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("DATA_NO_PASSADO")));

        verificarNadaMudou(anuncio, proposta, StatusReagendamento.PENDENTE);
    }

    @Test
    void dadoServicoQueDeixouDeEstarCombinado_quandoAContraparteAceita_entao409SemEfeito() throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO, Duration.ofDays(3));
        aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ESCOLHIDA);
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());

        aceitar(logar("rafael@example.com"), proposta.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));

        verificarNadaMudou(anuncio, proposta, StatusReagendamento.PENDENTE);
    }

    // ---------------------------------------------------------------- recusar

    @Test
    void dadaPropostaPendente_quandoAContraparteRecusa_entao200EADataOriginalContinuaVigente() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());

        recusar(logar("rafael@example.com"), proposta.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataHoraPretendida", is(anuncio.getDataHoraPretendida().toString())))
                .andExpect(jsonPath("$.reagendamentosAceitos", is(0)))
                .andExpect(jsonPath("$.propostaPendente").doesNotExist());

        Reagendamento depois = recarregar(proposta);
        assertThat(depois.getStatus()).isEqualTo(StatusReagendamento.RECUSADO);
        assertThat(depois.getRespondidoEm()).isEqualTo(agora);
        assertThat(recarregar(anuncio).getDataHoraPretendida()).isEqualTo(anuncio.getDataHoraPretendida());
        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(auditorias("REAGENDAMENTO_RECUSADO")).isEqualTo(1);
        List<ReagendamentoRecusadoEvent> publicados = eventos.stream(ReagendamentoRecusadoEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).proponenteId()).isEqualTo(maria.getId());
        assertThat(publicados.get(0).dataVigente()).isEqualTo(anuncio.getDataHoraPretendida());
    }

    // ---------------------------------------------------------------- autorização e entrada

    @Test
    void dadoUsuarioQueNaoEParteDoServico_quandoTentaResponder_entao404SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());
        usuario("Carlos Lima", "carlos@example.com", "11144477735", PapelUsuario.PRESTADOR);

        aceitar(logar("carlos@example.com"), proposta.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("REAGENDAMENTO_NAO_ENCONTRADO")));

        verificarNadaMudou(anuncio, proposta, StatusReagendamento.PENDENTE);
    }

    @Test
    void dadaPropostaInexistente_quandoTentaResponder_entao404() throws Exception {
        aceitar(logar("rafael@example.com"), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("REAGENDAMENTO_NAO_ENCONTRADO")));
    }

    @Test
    void dadaRequisicaoSemToken_quandoResponde_entao401() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());

        mockMvc.perform(post("/reagendamentos/{id}/responder", proposta.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decisao\":\"ACEITAR\"}"))
                .andExpect(status().isUnauthorized());

        verificarNadaMudou(anuncio, proposta, StatusReagendamento.PENDENTE);
    }

    @Test
    void dadaDecisaoAusenteOuInvalida_quandoResponde_entao400SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());
        String token = logar("rafael@example.com");

        responder(token, proposta.getId(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        responder(token, proposta.getId(), "{\"decisao\":\"TALVEZ\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        verificarNadaMudou(anuncio, proposta, StatusReagendamento.PENDENTE);
    }

    // ---------------------------------------------------------------- concorrência

    @Test
    void dadosAceiteECancelamentoSimultaneos_entaoUmVenceEOEstadoFinalFicaConsistente() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());
        String tokenRafael = logar("rafael@example.com");
        String tokenMaria = logar("maria@example.com");
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> aceite = executor.submit(() -> {
                largada.await();
                return codigo(aceitar(tokenRafael, proposta.getId()));
            });
            Future<Integer> cancelamento = executor.submit(() -> {
                largada.await();
                return codigo(mockMvc.perform(post("/reagendamentos/{id}/cancelar", proposta.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenMaria)));
            });
            largada.countDown();

            assertThat(List.of(aceite.get(20, TimeUnit.SECONDS), cancelamento.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            largada.countDown();
            executor.shutdownNow();
        }

        StatusReagendamento finalStatus = recarregar(proposta).getStatus();
        assertThat(finalStatus).isIn(StatusReagendamento.ACEITO, StatusReagendamento.CANCELADO);
        // a data vigente só mudou se, e somente se, o aceite venceu
        assertThat(recarregar(anuncio).getDataHoraPretendida()).isEqualTo(finalStatus == StatusReagendamento.ACEITO
                ? proposta.getDataProposta() : anuncio.getDataHoraPretendida());
        assertThat(auditorias("REAGENDAMENTO_ACEITO") + auditorias("REAGENDAMENTO_CANCELADO")).isEqualTo(1);
    }

    @Test
    void dadosAceiteERecusaSimultaneos_entaoUmVenceEOutroRecebe409() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());
        String token = logar("rafael@example.com");
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> aceite = executor.submit(() -> {
                largada.await();
                return codigo(aceitar(token, proposta.getId()));
            });
            Future<Integer> recusa = executor.submit(() -> {
                largada.await();
                return codigo(recusar(token, proposta.getId()));
            });
            largada.countDown();

            assertThat(List.of(aceite.get(20, TimeUnit.SECONDS), recusa.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            largada.countDown();
            executor.shutdownNow();
        }

        StatusReagendamento finalStatus = recarregar(proposta).getStatus();
        assertThat(finalStatus).isIn(StatusReagendamento.ACEITO, StatusReagendamento.RECUSADO);
        assertThat(recarregar(anuncio).getDataHoraPretendida()).isEqualTo(finalStatus == StatusReagendamento.ACEITO
                ? proposta.getDataProposta() : anuncio.getDataHoraPretendida());
        assertThat(eventosDeResposta()).isEqualTo(1);
    }

    /** Serviço cancelado pelo Empregador pelo endpoint real: a proposta perde o efeito e a candidatura encerra. */
    private Reagendamento propostaDeServicoCancelado(Anuncio anuncio, UUID proponenteId) throws Exception {
        Reagendamento proposta = pendenteDe(anuncio, proponenteId);
        mockMvc.perform(post("/anuncios/{id}/servico/cancelar", anuncio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar("maria@example.com")))
                .andExpect(status().isOk());
        assertThat(recarregar(proposta).getStatus()).isEqualTo(StatusReagendamento.SEM_EFEITO);
        return proposta;
    }

    @Test
    void dadoServicoCancelado_quandoOPrestadorQueFoiEscolhidoTentaAceitarOuRecusar_entao409ComoOEmpregadorENaoUm404()
            throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = propostaDeServicoCancelado(anuncio, maria.getId());
        String token = logar("rafael@example.com");

        aceitar(token, proposta.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("PROPOSTA_ENCERRADA")));
        recusar(token, proposta.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("PROPOSTA_ENCERRADA")));

        assertThat(recarregar(proposta).getStatus()).isEqualTo(StatusReagendamento.SEM_EFEITO);
        assertThat(auditorias("REAGENDAMENTO_ACEITO") + auditorias("REAGENDAMENTO_RECUSADO")).isZero();
    }
}
