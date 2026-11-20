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
import com.limio.api.comum.eventos.ReagendamentoCanceladoEvent;
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
 * BDD ponta a ponta (controller → banco real) de "Cancelar a própria proposta de reagendamento" (ETI-99, US46). O
 * "agora" é um {@link Clock} controlado, para fixar o prazo de resposta da proposta. As propostas entram por
 * repositório: o fluxo que cria é do commit anterior e o que as responde, do próximo.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class CancelarReagendamentoStoryTest {

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

    private ResultActions cancelar(String token, UUID reagendamentoId) throws Exception {
        return mockMvc.perform(post("/reagendamentos/{id}/cancelar", reagendamentoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private int cancelarCodigo(String token, UUID reagendamentoId) throws Exception {
        return cancelar(token, reagendamentoId).andReturn().getResponse().getStatus();
    }

    private Anuncio recarregar(Anuncio anuncio) {
        return anuncioRepository.findById(anuncio.getId()).orElseThrow();
    }

    private Reagendamento recarregar(Reagendamento reagendamento) {
        return reagendamentoRepository.findById(reagendamento.getId()).orElseThrow();
    }

    private long auditorias() {
        return jdbc.queryForObject("select count(*) from log_auditoria where tipo_acao = 'REAGENDAMENTO_CANCELADO'",
                Long.class);
    }

    private void verificarNadaMudou(Anuncio anuncio, Reagendamento proposta, StatusReagendamento statusEsperado) {
        assertThat(recarregar(proposta).getStatus()).isEqualTo(statusEsperado);
        assertThat(recarregar(proposta).getRespondidoEm()).isEqualTo(proposta.getRespondidoEm());
        assertThat(recarregar(anuncio).getDataHoraPretendida()).isEqualTo(anuncio.getDataHoraPretendida());
        assertThat(auditorias()).isZero();
        assertThat(eventos.stream(ReagendamentoCanceladoEvent.class).count()).isZero();
    }

    // ---------------------------------------------------------------- caminho feliz

    @Test
    void dadaPropostaPendenteDoEmpregador_quandoEleCancela_entao200EncerraAuditaEPublicaEvento() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());

        cancelar(logar("maria@example.com"), proposta.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(proposta.getId().toString())))
                .andExpect(jsonPath("$.status", is("CANCELADO")))
                .andExpect(jsonPath("$.dataVigente", is(anuncio.getDataHoraPretendida().toString())));

        Reagendamento depois = recarregar(proposta);
        assertThat(depois.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
        assertThat(depois.getRespondidoEm()).isEqualTo(agora);
        // a data original continua vigente e o serviço continua combinado
        assertThat(recarregar(anuncio).getDataHoraPretendida()).isEqualTo(anuncio.getDataHoraPretendida());
        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(auditorias()).isEqualTo(1);
        List<ReagendamentoCanceladoEvent> publicados = eventos.stream(ReagendamentoCanceladoEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).reagendamentoId()).isEqualTo(proposta.getId());
        assertThat(publicados.get(0).proponenteId()).isEqualTo(maria.getId());
        assertThat(publicados.get(0).prestadorId()).isEqualTo(rafael.getId());
    }

    @Test
    void dadaPropostaPendenteDoPrestador_quandoEleCancela_entao200() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, rafael.getId());

        cancelar(logar("rafael@example.com"), proposta.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELADO")));

        assertThat(recarregar(proposta).getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
    }

    @Test
    void dadaPropostaCancelada_quandoUmaDasPartesPropoeDeNovo_entaoNaoHaMaisPendenteImpedindo() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());
        cancelar(logar("maria@example.com"), proposta.getId()).andExpect(status().isOk());

        mockMvc.perform(post("/anuncios/{anuncioId}/reagendamentos", anuncio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar("rafael@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novaDataHora\":\"" + agora.plus(Duration.ofDays(6)) + "\",\"justificativa\":\"Outra data\"}"))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- autorização

    @Test
    void dadaPropostaDoEmpregador_quandoOPrestadorTentaCancelar_entao403SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());

        cancelar(logar("rafael@example.com"), proposta.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));

        verificarNadaMudou(anuncio, proposta, StatusReagendamento.PENDENTE);
    }

    @Test
    void dadaPropostaDoPrestador_quandoOEmpregadorTentaCancelar_entao403SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, rafael.getId());

        cancelar(logar("maria@example.com"), proposta.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));

        verificarNadaMudou(anuncio, proposta, StatusReagendamento.PENDENTE);
    }

    @Test
    void dadoUsuarioQueNaoEParteDoServico_quandoTentaCancelar_entao404SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());
        usuario("Carlos Lima", "carlos@example.com", "11144477735", PapelUsuario.PRESTADOR);

        cancelar(logar("carlos@example.com"), proposta.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("REAGENDAMENTO_NAO_ENCONTRADO")));

        verificarNadaMudou(anuncio, proposta, StatusReagendamento.PENDENTE);
    }

    @Test
    void dadaPropostaInexistente_quandoTentaCancelar_entao404() throws Exception {
        cancelar(logar("maria@example.com"), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("REAGENDAMENTO_NAO_ENCONTRADO")));
    }

    @Test
    void dadaRequisicaoSemToken_quandoCancela_entao401() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());

        mockMvc.perform(post("/reagendamentos/{id}/cancelar", proposta.getId()))
                .andExpect(status().isUnauthorized());

        verificarNadaMudou(anuncio, proposta, StatusReagendamento.PENDENTE);
    }

    // ---------------------------------------------------------------- estado e prazo

    @Test
    void dadaPropostaJaEncerrada_quandoOProponenteTentaCancelar_entao409SemEfeito() throws Exception {
        for (StatusReagendamento encerrado : List.of(StatusReagendamento.ACEITO, StatusReagendamento.RECUSADO,
                StatusReagendamento.CANCELADO, StatusReagendamento.EXPIRADO, StatusReagendamento.SEM_EFEITO)) {
            Anuncio anuncio = combinado(Duration.ofDays(3));
            Reagendamento proposta = proposta(anuncio, maria.getId(), encerrado, agora.plus(Duration.ofHours(24)));

            cancelar(logar("maria@example.com"), proposta.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo", is("PROPOSTA_ENCERRADA")));

            verificarNadaMudou(anuncio, proposta, encerrado);
        }
    }

    @Test
    void dadaPropostaPendenteQuePassouDoPrazo_quandoOProponenteTentaCancelar_entao409SemEfeito() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = proposta(anuncio, maria.getId(), StatusReagendamento.PENDENTE,
                agora.minus(Duration.ofMinutes(1)));

        cancelar(logar("maria@example.com"), proposta.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("PROPOSTA_EXPIRADA")));

        verificarNadaMudou(anuncio, proposta, StatusReagendamento.PENDENTE);
    }

    // ---------------------------------------------------------------- concorrência

    @Test
    void dadosDoisCancelamentosSimultaneos_entaoUmVenceAOutroRecebe409ESoHaUmRegistro() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());
        String token = logar("maria@example.com");
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = executor.submit(() -> {
                largada.await();
                return cancelarCodigo(token, proposta.getId());
            });
            Future<Integer> b = executor.submit(() -> {
                largada.await();
                return cancelarCodigo(token, proposta.getId());
            });
            largada.countDown();

            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            largada.countDown();
            executor.shutdownNow();
        }

        assertThat(recarregar(proposta).getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
        assertThat(auditorias()).isEqualTo(1);
        assertThat(eventos.stream(ReagendamentoCanceladoEvent.class).count()).isEqualTo(1);
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
    void dadoServicoCancelado_quandoOPrestadorProponenteTentaCancelarAProposta_entao409ENaoUm404() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = propostaDeServicoCancelado(anuncio, rafael.getId());

        cancelar(logar("rafael@example.com"), proposta.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("PROPOSTA_ENCERRADA")));

        assertThat(recarregar(proposta).getStatus()).isEqualTo(StatusReagendamento.SEM_EFEITO);
        assertThat(auditorias()).isZero();
    }
}
