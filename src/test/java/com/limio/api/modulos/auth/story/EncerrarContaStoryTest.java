package com.limio.api.modulos.auth.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.actions.helper.CpfHasher;
import com.limio.api.modulos.auth.actions.usecase.LimparRetencaoUseCase;
import com.limio.api.comum.auditoria.LogAuditoria;
import com.limio.api.comum.auditoria.LogAuditoriaRepository;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.modulos.auth.cpfbloqueado.CpfBloqueado;
import com.limio.api.modulos.auth.cpfbloqueado.CpfBloqueadoRepository;
import com.limio.api.modulos.auth.records.CadastroRequest;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.records.RefreshTokenRequest;
import com.limio.api.modulos.auth.sessao.Sessao;
import com.limio.api.modulos.auth.sessao.SessaoRepository;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do TICKET-0034/ETI-36 —
 * encerrar conta, anonimização e regras de retenção (LGPD). Sobe Spring
 * context + Testcontainers Postgres.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EncerrarContaStoryTest {

    private static final String EMAIL = "maria@example.com";
    private static final String SENHA = "senhaForte123";
    private static final String CPF = "52998224725";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SessaoRepository sessaoRepository;

    @Autowired
    private CpfBloqueadoRepository cpfBloqueadoRepository;

    @Autowired
    private LogAuditoriaRepository logAuditoriaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CpfHasher cpfHasher;

    @Autowired
    private LimparRetencaoUseCase limparRetencaoUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparBase() {
        logAuditoriaRepository.deleteAll();
        sessaoRepository.deleteAll();
        cpfBloqueadoRepository.deleteAll();
        // TRUNCATE ... CASCADE limpa também as tabelas filhas de usuario (verificacao_contato, sessao, perfis...)
        // que outras classes de story deixam para trás; deleteAll() esbarrava na FK.
        jdbcTemplate.execute("TRUNCATE TABLE usuario CASCADE");
    }

    // Outras classes de story usam o mesmo CPF fixo de teste (52998224725) como "válido" — não deixar
    // CpfBloqueado pra trás depois do último teste desta classe (mesmo cuidado de
    // AcessarContaEAlternarPapelStoryTest com Sessao).
    @AfterEach
    void naoDeixarCpfBloqueadoPraTras() {
        limparBase();
    }

    private Usuario cadastrar() {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto("Maria da Silva")
                .email(EMAIL)
                .telefone("11999998888")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(CPF)
                .dataNascimento(LocalDate.now().minusYears(25))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(StatusConta.ATIVA)
                .build());
    }

    private LoginResponse logar(String dispositivo) throws Exception {
        ResultActions resultado = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, SENHA, dispositivo))));
        return objectMapper.readValue(resultado.andReturn().getResponse().getContentAsString(), LoginResponse.class);
    }

    private ResultActions encerrarConta(String tokenAcesso, boolean confirmacao) throws Exception {
        return mockMvc.perform(post("/auth/encerrar-conta")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAcesso)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("confirmacao", confirmacao))));
    }

    @Test
    void dadoContaAtiva_quandoEncerraComConfirmacao_entaoAnonimizaBloqueiaCpfEEncerraTodasAsSessoes()
            throws Exception {
        Usuario usuario = cadastrar();
        var celular = logar("celular");
        var tablet = logar("tablet");

        encerrarConta(celular.token(), true).andExpect(status().isOk());

        Usuario atualizado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(atualizado.isEncerrada()).isTrue();
        assertThat(atualizado.getCpf()).isNull();
        assertThat(atualizado.getDataNascimento()).isNull();
        assertThat(atualizado.getCidadeUf()).isNull();
        assertThat(atualizado.getNomeCompleto()).isNotEqualTo("Maria da Silva");
        assertThat(atualizado.getEmail()).isNotEqualTo(EMAIL);
        assertThat(atualizado.getTelefone()).isNotEqualTo("11999998888");

        assertThat(sessaoRepository.findAll()).allSatisfy(sessao -> assertThat(sessao.getRevogadaEm()).isNotNull());

        CpfBloqueado bloqueio = cpfBloqueadoRepository.findAll().get(0);
        assertThat(bloqueio.getCpfHmac()).isEqualTo(cpfHasher.hash(CPF));
        assertThat(bloqueio.getLiberadoEm()).isAfter(Instant.now().plus(Duration.ofDays(360)));

        LogAuditoria entrada = logAuditoriaRepository.findAll().get(0);
        assertThat(entrada.getEntidade()).isEqualTo("Usuario");
        assertThat(entrada.getEntidadeId()).isEqualTo(usuario.getId());
        assertThat(entrada.getTipoAcao()).isEqualTo(TipoAcaoAuditoria.CONTA_ENCERRADA);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(tablet.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("SESSAO_INVALIDA")));
    }

    @Test
    void dadoContaEncerrada_quandoFazLogin_entaoRecusaComCredenciaisInvalidas() throws Exception {
        cadastrar();
        var celular = logar("celular");
        encerrarConta(celular.token(), true).andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, SENHA, null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("CREDENCIAIS_INVALIDAS")));
    }

    @Test
    void dadoSemConfirmacao_quandoEncerraConta_entaoRecusaSemMudarNada() throws Exception {
        Usuario usuario = cadastrar();
        var celular = logar("celular");

        encerrarConta(celular.token(), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        Usuario inalterado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertThat(inalterado.isEncerrada()).isFalse();
        assertThat(cpfBloqueadoRepository.count()).isZero();
    }

    @Test
    void dadoSemTokenDeAcesso_quandoEncerraConta_entaoRecusaComNaoAutenticado() throws Exception {
        mockMvc.perform(post("/auth/encerrar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confirmacao", true))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }

    @Test
    void dadoContaJaEncerrada_quandoEncerraDeNovoComOMesmoTokenAindaValido_entaoNaoDuplicaBloqueio()
            throws Exception {
        cadastrar();
        var celular = logar("celular");
        encerrarConta(celular.token(), true).andExpect(status().isOk());

        encerrarConta(celular.token(), true).andExpect(status().isOk());

        assertThat(cpfBloqueadoRepository.count()).isEqualTo(1);
        assertThat(logAuditoriaRepository.count()).isEqualTo(1);
    }

    @Test
    void dadoContaEncerradaHaMenosDe12Meses_quandoRecadastraComOMesmoCpf_entaoRecusa() throws Exception {
        cadastrar();
        var celular = logar("celular");
        encerrarConta(celular.token(), true).andExpect(status().isOk());

        CadastroRequest novoCadastro = new CadastroRequest(
                "Maria Nova", "maria.nova@example.com", "11988887777", "outraSenhaForte1", CPF,
                LocalDate.now().minusYears(30), "Rio de Janeiro/RJ");

        mockMvc.perform(post("/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novoCadastro)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CADASTRO_DADOS_INDISPONIVEIS")));
    }

    @Test
    void dadoRegistrosAntigos_quandoRodaALimpezaDeRetencao_entaoApagaSoOQueEstourouAValidade() {
        Instant agora = Instant.now();

        CpfBloqueado liberadoNoPassado = new CpfBloqueado();
        liberadoNoPassado.setCpfHmac("hmac-liberado-passado");
        liberadoNoPassado.setBloqueadoEm(agora.minus(Duration.ofDays(400)));
        liberadoNoPassado.setLiberadoEm(agora.minus(Duration.ofDays(1)));
        cpfBloqueadoRepository.save(liberadoNoPassado);

        CpfBloqueado aindaBloqueado = new CpfBloqueado();
        aindaBloqueado.setCpfHmac("hmac-ainda-bloqueado");
        aindaBloqueado.setBloqueadoEm(agora);
        aindaBloqueado.setLiberadoEm(agora.plus(Duration.ofDays(300)));
        cpfBloqueadoRepository.save(aindaBloqueado);

        LogAuditoria logAntigo = LogAuditoria.builder()
                .entidade("Usuario").tipoAcao(TipoAcaoAuditoria.CONTA_ENCERRADA).build();
        logAuditoriaRepository.save(logAntigo);
        forcarCriadoEm(logAntigo.getId(), agora.minus(Duration.ofDays(1826)));

        LogAuditoria logRecente = LogAuditoria.builder()
                .entidade("Usuario").tipoAcao(TipoAcaoAuditoria.CONTA_ENCERRADA).build();
        logAuditoriaRepository.save(logRecente);

        Usuario usuario = cadastrar();
        Sessao sessaoAntigaExpirada = Sessao.builder()
                .usuario(usuario).refreshTokenHash("hash-antiga").ultimoUsoEm(agora).expiraEm(agora.minus(Duration.ofDays(181)))
                .build();
        sessaoRepository.save(sessaoAntigaExpirada);
        Sessao sessaoAtiva = Sessao.builder()
                .usuario(usuario).refreshTokenHash("hash-ativa").ultimoUsoEm(agora).expiraEm(agora.plus(Duration.ofDays(30)))
                .build();
        sessaoRepository.save(sessaoAtiva);

        limparRetencaoUseCase.executar();

        assertThat(cpfBloqueadoRepository.findAll())
                .extracting(CpfBloqueado::getCpfHmac)
                .containsExactly("hmac-ainda-bloqueado");
        assertThat(logAuditoriaRepository.findAll()).hasSize(1);
        assertThat(sessaoRepository.findAll())
                .extracting(Sessao::getRefreshTokenHash)
                .containsExactly("hash-ativa");
    }

    @Test
    void dadoSessoesRevogadasEAtivas_quandoRodaALimpezaDeRetencao_entaoApagaSoRevogadaHaMaisDe6Meses() {
        Instant agora = Instant.now();
        Usuario usuario = cadastrar();
        Instant expiraNoFuturo = agora.plus(Duration.ofDays(30));

        // expiraEm no futuro isola o ramo "revogadaEm" da query (o ramo "expiraEm" já tem teste próprio).
        salvarSessao(usuario, "hash-revogada-antiga", expiraNoFuturo, agora.minus(Duration.ofDays(181)));
        salvarSessao(usuario, "hash-revogada-recente", expiraNoFuturo, agora.minus(Duration.ofDays(170)));
        Sessao ativaAntiga = salvarSessao(usuario, "hash-ativa-antiga", expiraNoFuturo, null);
        jdbcTemplate.update("update sessao set criado_em = ? where id = ?",
                java.sql.Timestamp.from(agora.minus(Duration.ofDays(400))), ativaAntiga.getId());

        limparRetencaoUseCase.executar();

        assertThat(sessaoRepository.findAll())
                .extracting(Sessao::getRefreshTokenHash)
                .containsExactlyInAnyOrder("hash-revogada-recente", "hash-ativa-antiga");
    }

    @Test
    void dadoLogDentroDoPrazoDe5Anos_quandoRodaALimpezaDeRetencao_entaoMantem() {
        LogAuditoria logDentroDoPrazo = LogAuditoria.builder()
                .entidade("Usuario").tipoAcao(TipoAcaoAuditoria.CONTA_ENCERRADA).build();
        logAuditoriaRepository.save(logDentroDoPrazo);
        forcarCriadoEm(logDentroDoPrazo.getId(), Instant.now().minus(Duration.ofDays(1820)));

        limparRetencaoUseCase.executar();

        assertThat(logAuditoriaRepository.findAll())
                .extracting(LogAuditoria::getId)
                .containsExactly(logDentroDoPrazo.getId());
    }

    @Test
    void dadoLimpezaJaExecutada_quandoRodaDeNovoNoMesmoDia_entaoNaoFalhaENaoApagaMais() {
        Instant agora = Instant.now();
        Usuario usuario = cadastrar();

        CpfBloqueado liberado = new CpfBloqueado();
        liberado.setCpfHmac("hmac-liberado");
        liberado.setBloqueadoEm(agora.minus(Duration.ofDays(400)));
        liberado.setLiberadoEm(agora.minus(Duration.ofDays(1)));
        cpfBloqueadoRepository.save(liberado);

        CpfBloqueado aindaBloqueado = new CpfBloqueado();
        aindaBloqueado.setCpfHmac("hmac-ainda-bloqueado");
        aindaBloqueado.setBloqueadoEm(agora);
        aindaBloqueado.setLiberadoEm(agora.plus(Duration.ofDays(300)));
        cpfBloqueadoRepository.save(aindaBloqueado);

        LogAuditoria logAntigo = LogAuditoria.builder()
                .entidade("Usuario").tipoAcao(TipoAcaoAuditoria.CONTA_ENCERRADA).build();
        logAuditoriaRepository.save(logAntigo);
        forcarCriadoEm(logAntigo.getId(), agora.minus(Duration.ofDays(1826)));
        logAuditoriaRepository.save(LogAuditoria.builder()
                .entidade("Usuario").tipoAcao(TipoAcaoAuditoria.CONTA_ENCERRADA).build());

        salvarSessao(usuario, "hash-revogada-antiga", agora.plus(Duration.ofDays(30)), agora.minus(Duration.ofDays(181)));
        salvarSessao(usuario, "hash-ativa", agora.plus(Duration.ofDays(30)), null);

        limparRetencaoUseCase.executar();
        long cpfs = cpfBloqueadoRepository.count();
        long logs = logAuditoriaRepository.count();
        long sessoes = sessaoRepository.count();
        assertThat(cpfs).isEqualTo(1);
        assertThat(logs).isEqualTo(1);
        assertThat(sessoes).isEqualTo(1);

        limparRetencaoUseCase.executar();

        assertThat(cpfBloqueadoRepository.count()).isEqualTo(cpfs);
        assertThat(logAuditoriaRepository.count()).isEqualTo(logs);
        assertThat(sessaoRepository.count()).isEqualTo(sessoes);
        assertThat(cpfBloqueadoRepository.findAll())
                .extracting(CpfBloqueado::getCpfHmac)
                .containsExactly("hmac-ainda-bloqueado");
        assertThat(sessaoRepository.findAll())
                .extracting(Sessao::getRefreshTokenHash)
                .containsExactly("hash-ativa");
    }

    @Test
    void dadoCpfDeContaEncerradaComCooldownVencido_quandoRodaALimpezaERecadastra_entaoCadastroConclui()
            throws Exception {
        cadastrar();
        var celular = logar("celular");
        encerrarConta(celular.token(), true).andExpect(status().isOk());

        // Simula o fim do cooldown de 12 meses: liberado ontem.
        jdbcTemplate.update("update cpf_bloqueado set liberado_em = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(1))));

        limparRetencaoUseCase.executar();

        assertThat(cpfBloqueadoRepository.findAll()).isEmpty();

        CadastroRequest novoCadastro = new CadastroRequest(
                "Maria Nova", "maria.nova@example.com", "11988887777", "outraSenhaForte1", CPF,
                LocalDate.now().minusYears(30), "Rio de Janeiro/RJ");

        mockMvc.perform(post("/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novoCadastro)))
                .andExpect(status().isCreated());
    }

    private Sessao salvarSessao(Usuario usuario, String hash, Instant expiraEm, Instant revogadaEm) {
        return sessaoRepository.save(Sessao.builder()
                .usuario(usuario).refreshTokenHash(hash).ultimoUsoEm(Instant.now())
                .expiraEm(expiraEm).revogadaEm(revogadaEm)
                .build());
    }

    /**
     * {@code criadoEm} é {@code @CreationTimestamp}: Hibernate ignora qualquer
     * valor setado em memória e não inclui a coluna num UPDATE normal — só SQL
     * nativo simula um registro antigo pro teste de retenção.
     */
    private void forcarCriadoEm(UUID id, Instant criadoEm) {
        jdbcTemplate.update("update log_auditoria set criado_em = ? where id = ?",
                java.sql.Timestamp.from(criadoEm), id);
    }
}
