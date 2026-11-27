package com.limio.api.modulos.anuncio.story;

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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.CausaEncerramento;
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
 * BDD ponta a ponta (controller → banco real) de "Consultar proposta de reagendamento" (ETI-99, US46). As propostas
 * entram por repositório. O {@link Clock} é um {@link MockitoBean} só para compartilhar o contexto Spring das demais
 * stories de reagendamento; a consulta em si não usa o relógio.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class ConsultarReagendamentoStoryTest {

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
                .causaEncerramento(status == StatusAnuncio.ENCERRADO ? CausaEncerramento.CANCELAMENTO : null)
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

    private ResultActions consultar(String token, UUID reagendamentoId) throws Exception {
        return mockMvc.perform(get("/reagendamentos/{id}", reagendamentoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void dadaPropostaPendente_quandoEmpregadorConsulta_entao200ComOsDadosDaProposta() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, rafael.getId());

        consultar(logar("maria@example.com"), proposta.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(proposta.getId().toString())))
                .andExpect(jsonPath("$.anuncioId", is(anuncio.getId().toString())))
                .andExpect(jsonPath("$.status", is("PENDENTE")))
                .andExpect(jsonPath("$.dataVigente", is(anuncio.getDataHoraPretendida().toString())))
                .andExpect(jsonPath("$.dataProposta", is(proposta.getDataProposta().toString())))
                .andExpect(jsonPath("$.justificativa", is(JUSTIFICATIVA)))
                .andExpect(jsonPath("$.proponenteId", is(rafael.getId().toString())))
                .andExpect(jsonPath("$.expiraEm", is(proposta.getExpiraEm().toString())))
                .andExpect(jsonPath("$.respondidoEm").doesNotExist());
    }

    @Test
    void dadaPropostaPendente_quandoPrestadorEscolhidoConsulta_entao200() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());

        consultar(logar("rafael@example.com"), proposta.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proponenteId", is(maria.getId().toString())));
    }

    @Test
    void dadaPropostaRecusadaDeUmServicoJaReagendado_quandoConsulta_entaoTrazADataVigenteAtualEORespondidoEm()
            throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = proposta(anuncio, maria.getId(), StatusReagendamento.RECUSADO,
                agora.plus(Duration.ofHours(24)));
        // outra proposta foi aceita depois e trocou a data vigente do serviço
        Instant novaVigente = agora.plus(Duration.ofDays(9));
        anuncio.setDataHoraPretendida(novaVigente);
        anuncioRepository.save(anuncio);

        consultar(logar("maria@example.com"), proposta.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RECUSADO")))
                .andExpect(jsonPath("$.dataVigente", is(novaVigente.toString())))
                .andExpect(jsonPath("$.dataAnterior", is(proposta.getDataAnterior().toString())))
                .andExpect(jsonPath("$.respondidoEm", is(proposta.getRespondidoEm().toString())));
    }

    @Test
    void dadoUsuarioQueNaoEParteDoServico_quandoConsulta_entao404() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());
        usuario("Carlos Lima", "carlos@example.com", "11144477735", PapelUsuario.PRESTADOR);

        consultar(logar("carlos@example.com"), proposta.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("REAGENDAMENTO_NAO_ENCONTRADO")));
    }

    @Test
    void dadaPropostaInexistente_quandoConsulta_entao404() throws Exception {
        consultar(logar("maria@example.com"), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("REAGENDAMENTO_NAO_ENCONTRADO")));
    }

    @Test
    void dadaRequisicaoSemToken_quandoConsulta_entao401() throws Exception {
        Anuncio anuncio = combinado(Duration.ofDays(3));
        Reagendamento proposta = pendenteDe(anuncio, maria.getId());

        mockMvc.perform(get("/reagendamentos/{id}", proposta.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    void dadoServicoCancelado_quandoOPrestadorQueFoiEscolhidoConsultaAProposta_entao200ComOStatusSemEfeito()
            throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.ENCERRADO, Duration.ofDays(3));
        aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.ENCERRADA); // cancelado: a candidatura encerra
        Reagendamento proposta = proposta(anuncio, maria.getId(), StatusReagendamento.SEM_EFEITO,
                agora.plus(Duration.ofHours(24)));

        consultar(logar("rafael@example.com"), proposta.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SEM_EFEITO")));
    }
}
