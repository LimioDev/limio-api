package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
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
import com.limio.api.comum.eventos.ReagendamentoPropostoEvent;
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
 * BDD ponta a ponta (controller → banco real) de "Propor reagendamento" (ETI-99, US46). O "agora" é um {@link Clock}
 * controlado, para fixar a data vigente já passada e o prazo de 24h da contraparte. Propostas anteriores (aceitas,
 * recusadas, expiradas) entram por repositório: os fluxos que as encerram são dos próximos commits do card.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class ProporReagendamentoStoryTest {

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
    private Reagendamento anterior(Anuncio anuncio, StatusReagendamento status, boolean aposDataVigente) {
        return reagendamentoRepository.save(Reagendamento.builder()
                .anuncioId(anuncio.getId())
                .dataAnterior(anuncio.getDataHoraPretendida())
                .dataProposta(anuncio.getDataHoraPretendida().plus(Duration.ofDays(2)))
                .status(status)
                .respondidoEm(status == StatusReagendamento.PENDENTE ? null : agora.minus(Duration.ofHours(1)))
                .proponenteId(maria.getId())
                .expiraEm(agora.plus(Duration.ofDays(1)))
                .propostaAposDataVigente(aposDataVigente)
                .build());
    }

    private String corpo(Instant novaDataHora, String justificativa) throws Exception {
        return objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
            if (novaDataHora != null) {
                put("novaDataHora", novaDataHora.toString());
            }
            if (justificativa != null) {
                put("justificativa", justificativa);
            }
        }});
    }

    private ResultActions propor(String token, UUID anuncioId, String corpo) throws Exception {
        return mockMvc.perform(post("/anuncios/{anuncioId}/reagendamentos", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions propor(String token, Anuncio anuncio, Duration dentroDeDias) throws Exception {
        return propor(token, anuncio.getId(), corpo(agora.plus(dentroDeDias), JUSTIFICATIVA));
    }

    private int proporCodigo(String token, Anuncio anuncio) throws Exception {
        return propor(token, anuncio, Duration.ofDays(5)).andReturn().getResponse().getStatus();
    }

    private Anuncio recarregar(Anuncio anuncio) {
        return anuncioRepository.findById(anuncio.getId()).orElseThrow();
    }

    private List<Reagendamento> propostasDe(Anuncio anuncio) {
        return reagendamentoRepository.findAll().stream()
                .filter(r -> r.getAnuncioId().equals(anuncio.getId()))
                .toList();
    }

    private long auditorias() {
        return jdbc.queryForObject("select count(*) from log_auditoria where tipo_acao = 'REAGENDAMENTO_PROPOSTO'",
                Long.class);
    }

    private void verificarNadaMudou(Anuncio anuncio, int propostasEsperadas) {
        assertThat(propostasDe(anuncio)).hasSize(propostasEsperadas);
        assertThat(recarregar(anuncio).getDataHoraPretendida()).isEqualTo(anuncio.getDataHoraPretendida());
        assertThat(auditorias()).isZero();
        assertThat(eventos.stream(ReagendamentoPropostoEvent.class).count()).isZero();
    }

    // ---------------------------------------------------------------- caminho feliz

    @Test
    void dadoServicoCombinado_quandoEmpregadorPropoe_entao200GravaPendenteComPrazoAuditaEPublicaEvento()
            throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Instant novaData = agora.plus(Duration.ofDays(5));

        propor(logar("maria@example.com"), anuncio.getId(), corpo(novaData, JUSTIFICATIVA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDENTE")))
                .andExpect(jsonPath("$.anuncioId", is(anuncio.getId().toString())))
                .andExpect(jsonPath("$.proponenteId", is(maria.getId().toString())))
                .andExpect(jsonPath("$.justificativa", is(JUSTIFICATIVA)));

        Reagendamento proposta = propostasDe(anuncio).get(0);
        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
        assertThat(proposta.getProponenteId()).isEqualTo(maria.getId());
        assertThat(proposta.getDataAnterior()).isEqualTo(anuncio.getDataHoraPretendida());
        assertThat(proposta.getDataProposta()).isEqualTo(novaData);
        assertThat(proposta.getExpiraEm()).isEqualTo(agora.plus(Duration.ofHours(24)));
        assertThat(proposta.isPropostaAposDataVigente()).isFalse();
        assertThat(proposta.getRespondidoEm()).isNull();
        // a data original continua vigente e o serviço continua combinado
        assertThat(recarregar(anuncio).getDataHoraPretendida()).isEqualTo(anuncio.getDataHoraPretendida());
        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(auditorias()).isEqualTo(1);
        List<ReagendamentoPropostoEvent> publicados = eventos.stream(ReagendamentoPropostoEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).reagendamentoId()).isEqualTo(proposta.getId());
        assertThat(publicados.get(0).proponenteId()).isEqualTo(maria.getId());
        assertThat(publicados.get(0).prestadorId()).isEqualTo(rafael.getId());
    }

    @Test
    void dadoServicoCombinado_quandoPrestadorEscolhidoPropoe_entao200ComOPrestadorComoProponente() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));

        propor(logar("rafael@example.com"), anuncio, Duration.ofDays(5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proponenteId", is(rafael.getId().toString())));

        assertThat(propostasDe(anuncio)).hasSize(1);
        assertThat(propostasDe(anuncio).get(0).getProponenteId()).isEqualTo(rafael.getId());
    }

    // ---------------------------------------------------------------- validação da proposta

    @Test
    void dadaNovaDataIgualAVigente_quandoPropoe_entao422SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Instant dataVigente = recarregar(anuncio).getDataHoraPretendida();

        propor(logar("maria@example.com"), anuncio.getId(), corpo(dataVigente, JUSTIFICATIVA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("DATA_IGUAL_A_VIGENTE")));

        verificarNadaMudou(anuncio, 0);
    }

    @Test
    void dadaNovaDataNoPassado_quandoPropoe_entao422SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));

        propor(logar("maria@example.com"), anuncio, Duration.ofHours(-1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("DATA_NO_PASSADO")));

        verificarNadaMudou(anuncio, 0);
    }

    @Test
    void dadaJustificativaEmBrancoOuAusente_quandoPropoe_entao400SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        String token = logar("maria@example.com");
        Instant novaData = agora.plus(Duration.ofDays(5));

        propor(token, anuncio.getId(), corpo(novaData, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        propor(token, anuncio.getId(), corpo(novaData, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        verificarNadaMudou(anuncio, 0);
    }

    @Test
    void dadaJustificativaMaiorQue600Caracteres_quandoPropoe_entao400SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        String token = logar("maria@example.com");
        Instant novaData = agora.plus(Duration.ofDays(5));

        propor(token, anuncio.getId(), corpo(novaData, "x".repeat(601)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        verificarNadaMudou(anuncio, 0);

        propor(token, anuncio.getId(), corpo(novaData, "x".repeat(600)))
                .andExpect(status().isOk());
    }

    @Test
    void dadaNovaDataAusente_quandoPropoe_entao400SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));

        propor(logar("maria@example.com"), anuncio.getId(), corpo(null, JUSTIFICATIVA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        verificarNadaMudou(anuncio, 0);
    }

    // ---------------------------------------------------------------- pendente e limite

    @Test
    void dadaPropostaPendente_quandoQualquerPartePropoeDeNovo_entao409SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        anterior(anuncio, StatusReagendamento.PENDENTE, false);

        propor(logar("rafael@example.com"), anuncio, Duration.ofDays(6))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("PROPOSTA_PENDENTE_EXISTENTE")));

        verificarNadaMudou(anuncio, 1);
    }

    @Test
    void dadoLimiteDeReagendamentosAceitos_quandoPropoe_entao409SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        anterior(anuncio, StatusReagendamento.ACEITO, false);
        anterior(anuncio, StatusReagendamento.ACEITO, false);

        propor(logar("maria@example.com"), anuncio, Duration.ofDays(6))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("LIMITE_REAGENDAMENTOS")));

        verificarNadaMudou(anuncio, 2);
    }

    @Test
    void dadasPropostasQueNaoForamAceitas_quandoPropoe_entaoNaoContamNoLimite() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        anterior(anuncio, StatusReagendamento.ACEITO, false);
        anterior(anuncio, StatusReagendamento.RECUSADO, false);
        anterior(anuncio, StatusReagendamento.CANCELADO, false);
        anterior(anuncio, StatusReagendamento.EXPIRADO, false);
        anterior(anuncio, StatusReagendamento.SEM_EFEITO, false);

        propor(logar("maria@example.com"), anuncio, Duration.ofDays(6)).andExpect(status().isOk());

        assertThat(propostasDe(anuncio)).hasSize(6);
    }

    // ---------------------------------------------------------------- depois da data vigente

    @Test
    void dadaDataVigenteJaPassada_quandoPropoeAPrimeira_entao200EMarcaAPropostaAposADataVigente() throws Exception {
        Anuncio anuncio = combinado(Duration.ofHours(-2));

        propor(logar("maria@example.com"), anuncio, Duration.ofDays(2)).andExpect(status().isOk());

        Reagendamento proposta = propostasDe(anuncio).get(0);
        assertThat(proposta.isPropostaAposDataVigente()).isTrue();
        assertThat(proposta.getDataAnterior()).isEqualTo(anuncio.getDataHoraPretendida());
        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
    }

    @Test
    void dadaDataVigenteJaPassadaEPropostaAnteriorRecusadaOuExpirada_quandoPropoeDeNovo_entao409SemEfeito()
            throws Exception {
        Anuncio recusada = combinado(Duration.ofHours(-2));
        anterior(recusada, StatusReagendamento.RECUSADO, true);

        propor(logar("maria@example.com"), recusada, Duration.ofDays(2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("REAGENDAMENTO_NAO_PERMITIDO")));

        verificarNadaMudou(recusada, 1);

        Anuncio expirada = combinado(Duration.ofHours(-3));
        anterior(expirada, StatusReagendamento.EXPIRADO, true);

        propor(logar("rafael@example.com"), expirada, Duration.ofDays(2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("REAGENDAMENTO_NAO_PERMITIDO")));

        verificarNadaMudou(expirada, 1);
    }

    // ---------------------------------------------------------------- estado e autorização

    @Test
    void dadoServicoForaDeCombinado_quandoPropoe_entao409SemEfeito() throws Exception {
        for (StatusAnuncio status : List.of(StatusAnuncio.EM_ANDAMENTO, StatusAnuncio.AGUARDANDO_VALIDACAO,
                StatusAnuncio.CONCLUIDO, StatusAnuncio.ENCERRADO)) {
            Anuncio anuncio = anuncio(status, Duration.ofDays(3));
            aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ESCOLHIDA);

            propor(logar("maria@example.com"), anuncio, Duration.ofDays(5))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));

            propor(logar("rafael@example.com"), anuncio, Duration.ofDays(5))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));

            verificarNadaMudou(anuncio, 0);
        }
    }

    @Test
    void dadoPublicadoSemPrestadorEscolhido_quandoEmpregadorPropoe_entao409SemEfeito() throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);

        propor(logar("maria@example.com"), anuncio, Duration.ofDays(5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));

        verificarNadaMudou(anuncio, 0);
    }

    @Test
    void dadoPrestadorComCandidaturaNaoEscolhida_quandoPropoe_entao404SemEfeito() throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, Duration.ofDays(3));
        aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENVIADA);

        propor(logar("rafael@example.com"), anuncio, Duration.ofDays(5))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));

        verificarNadaMudou(anuncio, 0);
    }

    @Test
    void dadoUsuarioQueNaoEParteDoServico_quandoPropoe_entao404SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        usuario("Carlos Lima", "carlos@example.com", "11144477735", PapelUsuario.PRESTADOR);

        propor(logar("carlos@example.com"), anuncio, Duration.ofDays(5))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));

        verificarNadaMudou(anuncio, 0);
    }

    @Test
    void dadoServicoInexistente_quandoPropoe_entao404() throws Exception {
        propor(logar("maria@example.com"), UUID.randomUUID(), corpo(agora.plus(Duration.ofDays(5)), JUSTIFICATIVA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoRequisicaoSemToken_quandoPropoe_entao401() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));

        mockMvc.perform(post("/anuncios/{anuncioId}/reagendamentos", anuncio.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(agora.plus(Duration.ofDays(5)), JUSTIFICATIVA)))
                .andExpect(status().isUnauthorized());

        verificarNadaMudou(anuncio, 0);
    }

    // ---------------------------------------------------------------- concorrência

    @Test
    void dadasDuasPropostasSimultaneasDasPartes_entaoUmaVenceAOutraRecebe409ESoHaUmaPendente() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        String tokenMaria = logar("maria@example.com");
        String tokenRafael = logar("rafael@example.com");
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> daMaria = executor.submit(() -> {
                largada.await();
                return proporCodigo(tokenMaria, anuncio);
            });
            Future<Integer> doRafael = executor.submit(() -> {
                largada.await();
                return proporCodigo(tokenRafael, anuncio);
            });
            largada.countDown();

            assertThat(List.of(daMaria.get(20, TimeUnit.SECONDS), doRafael.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            largada.countDown();
            executor.shutdownNow();
        }

        assertThat(propostasDe(anuncio)).hasSize(1);
        assertThat(propostasDe(anuncio).get(0).getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
        assertThat(auditorias()).isEqualTo(1);
        assertThat(eventos.stream(ReagendamentoPropostoEvent.class).count()).isEqualTo(1);
    }

    @Test
    void dadoServicoCancelado_quandoOPrestadorQueFoiEscolhidoPropoe_entao409ComoOEmpregadorENaoUm404()
            throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.ENCERRADO, Duration.ofDays(3));
        aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENCERRADA); // cancelado: a candidatura encerra

        propor(logar("rafael@example.com"), anuncio, Duration.ofDays(5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));
        propor(logar("maria@example.com"), anuncio, Duration.ofDays(5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));

        verificarNadaMudou(anuncio, 0);
    }
}
