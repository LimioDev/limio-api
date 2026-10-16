package com.limio.api.modulos.auth.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.excecao.enums.CodigoErro;
import com.limio.api.modulos.auth.actions.helper.TokenRecuperacaoHelper;
import com.limio.api.modulos.auth.actions.service.EmailService;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoService;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) das trocas de e-mail e de telefone
 * (TICKET-0048). Caixa-preta via HTTP; SQL só pra limpar e pré-popular. O envio de
 * e-mail é espiado ({@link EmailService} real, que só loga com {@code email.enviar=false}).
 * Não há UC05 nem UC04: nenhum teste aqui confirma e-mail ou telefone.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TrocarEmailETelefoneStoryTest {

    private static final String SENHA = "senhaForte123";
    private static final Conta MARIA = new Conta("Maria da Silva", "maria@example.com", "11999998888", "52998224725");
    private static final Conta JOAO = new Conta("João Pereira", "joao@example.com", "21988887777", "11144477735");
    private static final String PREFIXO_LINK = "https://app.limio.test/confirmar-email?token=";

    private record Conta(String nome, String email, String telefone, String cpf) {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VerificacaoContatoService verificacaoContatoService;

    @MockitoSpyBean
    private EmailService emailService;

    @MockitoSpyBean
    private UsuarioService usuarioService;

    @BeforeEach
    void limparBase() {
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from verificacao_contato");
        jdbc.execute("delete from sessao");
        jdbc.execute("delete from tentativa_login");
        jdbc.execute("delete from usuario");
    }

    // ---------- apoio ----------

    private String cadastrarELogar(Conta conta) throws Exception {
        mockMvc.perform(post("/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "nomeCompleto", conta.nome(), "email", conta.email(), "telefone", conta.telefone(),
                        "senha", SENHA, "cpf", conta.cpf(), "dataNascimento", "1990-01-01",
                        "cidadeUf", "São Paulo/SP"))))
                .andExpect(status().isCreated());
        return logar(conta.email()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::tokenDe);
    }

    private ResultActions logar(String email) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "senha", SENHA, "dispositivo", "cel"))));
    }

    private String tokenDe(String corpoLogin) {
        return objectMapper.readTree(corpoLogin).get("token").asString();
    }

    private ResultActions trocarEmail(String token, String novoEmail, String senha) throws Exception {
        return mockMvc.perform(patch("/auth/email")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("novoEmail", novoEmail, "senhaAtual", senha))));
    }

    private ResultActions trocarTelefone(String token, String novoTelefone, String senha) throws Exception {
        return mockMvc.perform(patch("/auth/telefone")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("novoTelefone", novoTelefone, "senhaAtual", senha))));
    }

    private UUID idDe(Conta conta) {
        return jdbc.queryForObject("select id from usuario where cpf = ?", UUID.class, conta.cpf());
    }

    private String emailNoBanco(UUID id) {
        return jdbc.queryForObject("select email from usuario where id = ?", String.class, id);
    }

    private String telefoneNoBanco(UUID id) {
        return jdbc.queryForObject("select telefone from usuario where id = ?", String.class, id);
    }

    private boolean emailConfirmado(UUID id) {
        return jdbc.queryForObject("select email_confirmado_em is not null from usuario where id = ?", Boolean.class, id);
    }

    private boolean telefoneConfirmado(UUID id) {
        return jdbc.queryForObject("select telefone_confirmado_em is not null from usuario where id = ?", Boolean.class,
                id);
    }

    private void confirmarContatos(UUID id) {
        jdbc.update("update usuario set email_confirmado_em = now(), telefone_confirmado_em = now() where id = ?", id);
    }

    private int tokens(UUID id) {
        return jdbc.queryForObject("select count(*) from verificacao_contato where usuario_id = ?", Integer.class, id);
    }

    private int tokensPendentes(UUID id) {
        return jdbc.queryForObject(
                "select count(*) from verificacao_contato where usuario_id = ? and consumido_em is null",
                Integer.class, id);
    }

    private List<String> linksEnviadosPara(String destinatario, int vezes) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(vezes)).enviarConfirmacaoEmail(eq(destinatario), link.capture());
        return link.getAllValues();
    }

    private static String tokenDoLink(String link) {
        assertThat(link).startsWith(PREFIXO_LINK);
        return link.substring(PREFIXO_LINK.length());
    }

    private List<Integer> emParalelo(List<Callable<Integer>> tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (Callable<Integer> tarefa : tarefas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();
            List<Integer> status = new ArrayList<>();
            for (Future<Integer> futuro : futuros) {
                status.add(futuro.get());
            }
            return status;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long contar(List<Integer> status, int valor) {
        return status.stream().filter(s -> s == valor).count();
    }

    // ---------- troca de e-mail ----------

    @Test
    void dadoEmailNovoLivre_quandoTroca_entaoGravaLimpaConfirmacaoEEmiteUmUnicoLink() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);
        confirmarContatos(id);

        trocarEmail(token, "  Novo@Example.com ", SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("novo@example.com")))
                .andExpect(jsonPath("$.emailConfirmadoEm", nullValue()));

        assertThat(emailNoBanco(id)).isEqualTo("novo@example.com");
        assertThat(emailConfirmado(id)).isFalse();
        assertThat(telefoneConfirmado(id)).as("troca de e-mail não toca o telefone").isTrue();
        Map<String, Object> linha = jdbc.queryForMap(
                "select canal, finalidade, length(token_hash) as tamanho, expira_em > now() as futuro,"
                        + " consumido_em from verificacao_contato where usuario_id = ?", id);
        assertThat(linha).containsEntry("canal", "EMAIL").containsEntry("finalidade", "CONFIRMACAO_EMAIL")
                .containsEntry("tamanho", 64).containsEntry("futuro", true);
        assertThat(linha.get("consumido_em")).isNull();
        logar("novo@example.com").andExpect(status().isOk());
        logar(MARIA.email()).andExpect(status().isUnauthorized());
    }

    @Test
    void dadoLinkEnviado_entaoGuardaSoOHashDoTokenENaoServeParaRedefinirSenha() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);

        trocarEmail(token, "novo@example.com", SENHA).andExpect(status().isOk());

        String tokenDoLink = tokenDoLink(linksEnviadosPara("novo@example.com", 1).get(0));
        String hashNoBanco = jdbc.queryForObject("select token_hash from verificacao_contato where usuario_id = ?",
                String.class, id);
        assertThat(hashNoBanco).isEqualTo(TokenRecuperacaoHelper.hash(tokenDoLink)).isNotEqualTo(tokenDoLink);
        mockMvc.perform(post("/auth/redefinir-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", tokenDoLink, "novaSenha", "senhaNova456"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("TOKEN_RECUPERACAO_INVALIDO")));
    }

    @Test
    void dadoDuasTrocas_entaoOTokenDoEnderecoAnteriorFicaInvalidoESoOUltimoValeNoFormatoConsumivel() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);

        trocarEmail(token, "um@example.com", SENHA).andExpect(status().isOk());
        trocarEmail(token, "dois@example.com", SENHA).andExpect(status().isOk());

        assertThat(tokens(id)).isEqualTo(2);
        assertThat(tokensPendentes(id)).isEqualTo(1);
        String tokenAntigo = tokenDoLink(linksEnviadosPara("um@example.com", 1).get(0));
        String tokenAtual = tokenDoLink(linksEnviadosPara("dois@example.com", 1).get(0));
        // formato gravado é consumível por uma UC05 futura (aqui só se confere o dado, sem implementá-la)
        Instant agora = Instant.now();
        assertThat(verificacaoContatoService.buscarPorTokenHashEFinalidade(TokenRecuperacaoHelper.hash(tokenAntigo),
                FinalidadeVerificacao.CONFIRMACAO_EMAIL).orElseThrow().isValido(agora)).isFalse();
        assertThat(verificacaoContatoService.buscarPorTokenHashEFinalidade(TokenRecuperacaoHelper.hash(tokenAtual),
                FinalidadeVerificacao.CONFIRMACAO_EMAIL).orElseThrow().isValido(agora)).isTrue();
    }

    @Test
    void dadoEmailDeOutraConta_quandoTroca_entao409GenericoSemEfeitos() throws Exception {
        String tokenMaria = cadastrarELogar(MARIA);
        cadastrarELogar(JOAO);
        UUID idMaria = idDe(MARIA);

        trocarEmail(tokenMaria, "JOAO@Example.com", SENHA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CONTATO_NAO_PERMITIDO")))
                .andExpect(jsonPath("$.mensagem", is(CodigoErro.CONTATO_NAO_PERMITIDO.mensagemPadrao())));

        assertThat(emailNoBanco(idMaria)).isEqualTo(MARIA.email());
        assertThat(tokens(idMaria)).isZero();
        verifyNoInteractions(emailService);
    }

    @Test
    void dadoViolacaoRealDaConstraint_quandoTroca_entao409SemTokenNemEmailENaoEnvenenaAConexao() throws Exception {
        String tokenMaria = cadastrarELogar(MARIA);
        cadastrarELogar(JOAO);
        UUID idMaria = idDe(MARIA);
        confirmarContatos(idMaria);
        // simula a corrida: a pré-checagem não enxerga o e-mail do João, então o UPDATE real bate em uk_usuario_email
        doReturn(Optional.empty()).when(usuarioService).buscarPorEmail(JOAO.email());

        trocarEmail(tokenMaria, JOAO.email(), SENHA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CONTATO_NAO_PERMITIDO")));

        assertThat(emailNoBanco(idMaria)).isEqualTo(MARIA.email());
        assertThat(emailConfirmado(idMaria)).as("confirmação preservada: nada foi aplicado").isTrue();
        assertThat(tokens(idMaria)).isZero();
        verify(emailService, never()).enviarConfirmacaoEmail(any(), any());
        trocarEmail(tokenMaria, "livre@example.com", SENHA).andExpect(status().isOk());
        assertThat(emailNoBanco(idMaria)).isEqualTo("livre@example.com");
    }

    @Test
    void dadoViolacaoRealDaConstraint_entaoTokenPendenteAnteriorContinuaPendente() throws Exception {
        String tokenMaria = cadastrarELogar(MARIA);
        cadastrarELogar(JOAO);
        UUID idMaria = idDe(MARIA);
        trocarEmail(tokenMaria, "antes@example.com", SENHA).andExpect(status().isOk());
        doReturn(Optional.empty()).when(usuarioService).buscarPorEmail(JOAO.email());

        trocarEmail(tokenMaria, JOAO.email(), SENHA).andExpect(status().isConflict());

        assertThat(emailNoBanco(idMaria)).isEqualTo("antes@example.com");
        assertThat(tokens(idMaria)).isEqualTo(1);
        assertThat(tokensPendentes(idMaria)).isEqualTo(1);
    }

    @Test
    void dadoTrocaComSucesso_entaoOEmailSoSaiDepoisDoCommitComOTokenJaVisivelParaOutraConexao() throws Exception {
        String token = cadastrarELogar(MARIA);
        AtomicBoolean tokenVisivelNoEnvio = new AtomicBoolean();
        doAnswer(invocacao -> {
            String hash = TokenRecuperacaoHelper.hash(tokenDoLink(invocacao.getArgument(1)));
            // JdbcTemplate usa outra conexão: só enxerga o que já foi comitado
            tokenVisivelNoEnvio.set(jdbc.queryForObject(
                    "select count(*) from verificacao_contato where token_hash = ?", Integer.class, hash) == 1);
            return invocacao.callRealMethod();
        }).when(emailService).enviarConfirmacaoEmail(any(), any());

        trocarEmail(token, "novo@example.com", SENHA).andExpect(status().isOk());

        assertThat(tokenVisivelNoEnvio).isTrue();
    }

    @Test
    void dadoEmailIgualAoAtualNaoConfirmado_quandoTroca_entaoReemiteOLink() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);
        trocarEmail(token, MARIA.email(), SENHA).andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(MARIA.email())));
        String primeiro = tokenDoLink(linksEnviadosPara(MARIA.email(), 1).get(0));

        trocarEmail(token, "MARIA@example.com", SENHA).andExpect(status().isOk());

        assertThat(emailNoBanco(id)).isEqualTo(MARIA.email());
        assertThat(tokens(id)).isEqualTo(2);
        assertThat(tokensPendentes(id)).isEqualTo(1);
        List<String> links = linksEnviadosPara(MARIA.email(), 2);
        assertThat(tokenDoLink(links.get(1))).isNotEqualTo(primeiro);
        assertThat(jdbc.queryForObject("select consumido_em is not null from verificacao_contato where token_hash = ?",
                Boolean.class, TokenRecuperacaoHelper.hash(primeiro))).isTrue();
    }

    @Test
    void dadoEmailIgualAoAtualJaConfirmado_quandoTroca_entaoNaoFazNada() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);
        confirmarContatos(id);

        trocarEmail(token, MARIA.email(), SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(MARIA.email())));

        assertThat(emailConfirmado(id)).isTrue();
        assertThat(tokens(id)).isZero();
        verifyNoInteractions(emailService);
    }

    @Test
    void dadoReemissoesRepetidas_quandoPassaDoLimite_entao429SemNovoToken() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);

        for (int i = 0; i < 3; i++) {
            trocarEmail(token, MARIA.email(), SENHA).andExpect(status().isOk());
        }
        trocarEmail(token, MARIA.email(), SENHA)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo", is("TROCA_CONTATO_LIMITE_EXCEDIDO")));
        trocarEmail(token, "outro@example.com", SENHA).andExpect(status().isTooManyRequests());

        assertThat(tokens(id)).isEqualTo(3);
        assertThat(emailNoBanco(id)).isEqualTo(MARIA.email());
        verify(emailService, times(3)).enviarConfirmacaoEmail(any(), any());
    }

    @Test
    void dadoRequestsInvalidos_quandoTrocaEmail_entao400() throws Exception {
        String token = cadastrarELogar(MARIA);

        trocarEmail(token, "sem-arroba", SENHA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        trocarEmail(token, "novo@example.com", " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        trocarEmail(token, "a".repeat(250) + "@example.com", SENHA).andExpect(status().isBadRequest());

        assertThat(emailNoBanco(idDe(MARIA))).isEqualTo(MARIA.email());
    }

    @Test
    void dadoSenhaAtualErrada_quandoTrocaEmailOuTelefone_entao401SemAlterar() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);

        trocarEmail(token, "novo@example.com", "senhaErrada1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("SENHA_ATUAL_INCORRETA")));
        trocarTelefone(token, "21988887777", "senhaErrada1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("SENHA_ATUAL_INCORRETA")));

        assertThat(emailNoBanco(id)).isEqualTo(MARIA.email());
        assertThat(telefoneNoBanco(id)).isEqualTo(MARIA.telefone());
        assertThat(tokens(id)).isZero();
    }

    // ---------- troca de telefone ----------

    @Test
    void dadoCelularValido_quandoTrocaTelefone_entaoBancoGuardaDigitosEApiDevolveMaisCinquentaECinco() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);
        confirmarContatos(id);
        assertThat(telefoneNoBanco(id)).as("cadastro e troca guardam o mesmo formato").isEqualTo("11999998888");

        trocarTelefone(token, "(21) 98888-7777", SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone", is("+5521988887777")))
                .andExpect(jsonPath("$.telefoneConfirmadoEm", nullValue()));

        assertThat(telefoneNoBanco(id)).isEqualTo("21988887777");
        assertThat(jdbc.queryForObject("select length(telefone) from usuario where id = ?", Integer.class, id))
                .isEqualTo(11);
        assertThat(telefoneConfirmado(id)).isFalse();
        assertThat(emailConfirmado(id)).as("troca de telefone não toca o e-mail").isTrue();
        assertThat(tokens(id)).as("sem OTP/SMS: nenhum token é criado").isZero();
        verifyNoInteractions(emailService);
    }

    @Test
    void dadoFormatosAceitos_quandoTrocaTelefone_entaoTodosViramOsMesmosDigitosNacionais() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);

        for (String entrada : List.of("21988887777", "+55 21 98888-7777", "5521988887777", " 21 9 8888 7777 ")) {
            jdbc.update("update usuario set telefone = '11999998888' where id = ?", id);
            trocarTelefone(token, entrada, SENHA).andExpect(status().isOk())
                    .andExpect(jsonPath("$.telefone", is("+5521988887777")));
            assertThat(telefoneNoBanco(id)).as(entrada).isEqualTo("21988887777");
        }
    }

    @Test
    void dadoTelefoneLegadoDeDezDigitos_quandoTrocaTelefone_entaoSubstituiPorCelularValido() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);
        jdbc.update("update usuario set telefone = '1133334444' where id = ?", id);

        trocarTelefone(token, "11999998888", SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone", is("+5511999998888")));

        assertThat(telefoneNoBanco(id)).isEqualTo("11999998888");
    }

    @Test
    void dadoTelefoneLegadoDeDezDigitos_quandoTrocaOEmail_entaoAApiDevolveMaisCinquentaECincoEODigitosLegados()
            throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);
        jdbc.update("update usuario set telefone = '1133334444' where id = ?", id);

        trocarEmail(token, "novo@example.com", SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone", is("+551133334444")));

        assertThat(telefoneNoBanco(id)).as("a troca de e-mail não reescreve o telefone").isEqualTo("1133334444");
    }

    @Test
    void dadoTelefoneInvalido_quandoTrocaTelefone_entao400SemAlterar() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);

        for (String invalido : List.of("1133334444", "11899998888", "01999998888", "1199999888", "abc", "")) {
            trocarTelefone(token, invalido, SENHA).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        }

        assertThat(telefoneNoBanco(id)).isEqualTo(MARIA.telefone());
    }

    @Test
    void dadoTelefoneIgualAoAtual_quandoTrocaTelefone_entaoNaoFazNada() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);
        confirmarContatos(id);

        trocarTelefone(token, "+55 (11) 99999-8888", SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone", is("+5511999998888")));

        assertThat(telefoneNoBanco(id)).isEqualTo("11999998888");
        assertThat(telefoneConfirmado(id)).as("mesmo número já armazenado: confirmação preservada").isTrue();
    }

    // ---------- isolamento, autorização e concorrência ----------

    @Test
    void dadoDuasContas_quandoUmaTroca_entaoAOutraFicaIntacta() throws Exception {
        String tokenMaria = cadastrarELogar(MARIA);
        cadastrarELogar(JOAO);
        UUID idJoao = idDe(JOAO);
        confirmarContatos(idJoao);

        trocarEmail(tokenMaria, "maria.nova@example.com", SENHA).andExpect(status().isOk());
        trocarTelefone(tokenMaria, "31977776666", SENHA).andExpect(status().isOk());

        assertThat(emailNoBanco(idJoao)).isEqualTo(JOAO.email());
        assertThat(telefoneNoBanco(idJoao)).isEqualTo(JOAO.telefone());
        assertThat(emailConfirmado(idJoao)).isTrue();
        assertThat(telefoneConfirmado(idJoao)).isTrue();
        assertThat(tokens(idJoao)).isZero();
    }

    @Test
    void dadoSemTokenOuContaImpedida_quandoTroca_entaoRecusa() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);

        mockMvc.perform(patch("/auth/email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"novoEmail\":\"a@example.com\",\"senhaAtual\":\"x\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/auth/telefone").contentType(MediaType.APPLICATION_JSON)
                .content("{\"novoTelefone\":\"21988887777\",\"senhaAtual\":\"x\"}")).andExpect(status().isUnauthorized());

        jdbc.update("update usuario set status_conta = 'BLOQUEADA' where id = ?", id);
        trocarEmail(token, "novo@example.com", SENHA).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("CONTA_SUSPENSA")));
        trocarTelefone(token, "21988887777", SENHA).andExpect(status().isForbidden());

        jdbc.update("update usuario set status_conta = 'ATIVA', anonimizado_em = now() where id = ?", id);
        trocarEmail(token, "novo@example.com", SENHA).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("SESSAO_INVALIDA")));
        trocarTelefone(token, "21988887777", SENHA).andExpect(status().isUnauthorized());

        assertThat(emailNoBanco(id)).isEqualTo(MARIA.email());
        assertThat(telefoneNoBanco(id)).isEqualTo(MARIA.telefone());
    }

    @Test
    void dadoSeisReemissoesSimultaneasDaMesmaConta_entaoOLockMantemOLimiteDeTres() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);

        List<Callable<Integer>> tarefas = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tarefas.add(() -> trocarEmail(token, MARIA.email(), SENHA).andReturn().getResponse().getStatus());
        }
        List<Integer> status = emParalelo(tarefas);

        assertThat(contar(status, 200)).isEqualTo(3);
        assertThat(contar(status, 429)).isEqualTo(3);
        assertThat(tokens(id)).isEqualTo(3);
        assertThat(tokensPendentes(id)).isEqualTo(1);
    }

    @Test
    void dadoSeisTrocasSimultaneasParaEmailsDiferentes_entaoOLockMantemOLimiteDeTres() throws Exception {
        String token = cadastrarELogar(MARIA);
        UUID id = idDe(MARIA);

        List<String> emails = new ArrayList<>();
        List<Callable<Integer>> tarefas = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String novo = "troca" + i + "@example.com";
            emails.add(novo);
            tarefas.add(() -> trocarEmail(token, novo, SENHA).andReturn().getResponse().getStatus());
        }
        List<Integer> status = emParalelo(tarefas);

        assertThat(contar(status, 200)).isEqualTo(3);
        assertThat(contar(status, 429)).isEqualTo(3);
        assertThat(tokens(id)).isEqualTo(3);
        assertThat(tokensPendentes(id)).isEqualTo(1);
        List<String> aceitos = new ArrayList<>();
        for (int i = 0; i < emails.size(); i++) {
            if (status.get(i) == 200) {
                aceitos.add(emails.get(i));
            }
        }
        assertThat(aceitos).contains(emailNoBanco(id));
    }

    @Test
    void dadoDuasContas_quandoReemitemEmParalelo_entaoCadaUmaTemSeuProprioLimite() throws Exception {
        String tokenMaria = cadastrarELogar(MARIA);
        String tokenJoao = cadastrarELogar(JOAO);

        List<Callable<Integer>> tarefas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            tarefas.add(() -> trocarEmail(tokenMaria, MARIA.email(), SENHA).andReturn().getResponse().getStatus());
            tarefas.add(() -> trocarEmail(tokenJoao, JOAO.email(), SENHA).andReturn().getResponse().getStatus());
        }
        List<Integer> status = emParalelo(tarefas);

        assertThat(contar(status, 200)).isEqualTo(6);
        assertThat(tokens(idDe(MARIA))).isEqualTo(3);
        assertThat(tokens(idDe(JOAO))).isEqualTo(3);
    }

    @Test
    void dadoDuasContasTrocandoParaOMesmoEmailAoMesmoTempo_entaoUmaVenceEAOutraRecebe409NuncaErro500()
            throws Exception {
        String tokenMaria = cadastrarELogar(MARIA);
        String tokenJoao = cadastrarELogar(JOAO);

        List<Integer> status = emParalelo(List.of(
                () -> trocarEmail(tokenMaria, "disputado@example.com", SENHA).andReturn().getResponse().getStatus(),
                () -> trocarEmail(tokenJoao, "disputado@example.com", SENHA).andReturn().getResponse().getStatus()));

        assertThat(contar(status, 200)).isEqualTo(1);
        assertThat(contar(status, 409)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from usuario where email = 'disputado@example.com'",
                Integer.class)).isEqualTo(1);
    }
}
