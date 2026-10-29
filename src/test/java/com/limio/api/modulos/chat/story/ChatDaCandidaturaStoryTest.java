package com.limio.api.modulos.chat.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.AnuncioCanceladoEvent;
import com.limio.api.comum.eventos.CandidaturaCriadaEvent;
import com.limio.api.comum.seguranca.JwtAuthFilter;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.chat.OutraParte;
import com.limio.api.modulos.chat.enums.TipoEventoChat;
import com.limio.api.modulos.chat.records.EventoChatResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta do ETI-101: evento de {@code anuncio} -> listener de {@code chat} -> banco real, rotas HTTP e o
 * canal em tempo real com um cliente STOMP de verdade. Ainda não há rota de candidatura na develop (ETI-75, PR #19):
 * anúncio e candidatura são semeados pelo repository e o {@code CandidaturaCriadaEvent} é publicado numa transação,
 * do mesmo jeito que o usecase de candidatura vai publicar.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ChatDaCandidaturaStoryTest {

    private static final String SENHA = "senhaForte123";
    private static final Duration ESPERA = Duration.ofSeconds(5);

    @LocalServerPort
    private int porta;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationEventPublisher eventos;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final List<StompSession> sessoes = new ArrayList<>();

    private Usuario empregador;
    private Usuario prestador;
    private Anuncio anuncio;
    private Aplicacao candidatura;
    private String categoriaNome;

    @BeforeEach
    void prepararCandidatura() {
        limparBase();
        empregador = cadastrar("Marina Bertoldi", "marina@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        prestador = cadastrar("Rafael Souza", "rafael@example.com", "11144477735", PapelUsuario.PRESTADOR);
        Map<String, Object> categoria = jdbc.queryForMap("select id, nome from categoria where protegida");
        categoriaNome = (String) categoria.get("nome");
        anuncio = anuncioRepository.save(Anuncio.builder()
                .titulo("Instalar 3 tomadas")
                .categoriaId((UUID) categoria.get("id"))
                .contratanteId(empregador.getId())
                .descricao("Trocar três tomadas da cozinha")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(3)))
                .status(StatusAnuncio.PUBLICADO)
                .build());
        candidatura = aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(prestador.getId())
                .status(StatusAplicacao.ENVIADA)
                .dataAplicacao(Instant.now())
                .build());
    }

    @AfterEach
    void limpar() {
        sessoes.forEach(sessao -> {
            if (sessao.isConnected())
                sessao.disconnect();
        });
        limparBase();
    }

    private void limparBase() {
        jdbc.execute("delete from mensagem");
        jdbc.execute("delete from chat");
        jdbc.execute("delete from aplicacao");
        jdbc.execute("delete from anuncio");
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from cpf_bloqueado");
        jdbc.execute("TRUNCATE TABLE usuario CASCADE");
    }

    private Usuario cadastrar(String nome, String email, String cpf, PapelUsuario papel) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nome)
                .email(email)
                .telefone("11999998888")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(30))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(papel)
                .statusConta(StatusConta.ATIVA)
                .build());
    }

    private String logar(Usuario usuario) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(usuario.getEmail(), SENHA, "celular"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    /** Mesmo caminho do usecase de candidatura: evento publicado dentro da transação, chat criado depois do commit. */
    private UUID candidaturaCriada() {
        transacao.executeWithoutResult(status -> eventos.publishEvent(new CandidaturaCriadaEvent(candidatura.getId(),
                anuncio.getId(), anuncio.getTitulo(), empregador.getId(), prestador.getId(), Instant.now())));
        return jdbc.queryForObject("select id from chat where aplicacao_id = ?", UUID.class, candidatura.getId());
    }

    /** Token de acesso que vence em {@code validade}, no formato lido pelo {@link JwtAuthFilter}. */
    private String tokenQueVenceEm(Usuario usuario, Duration validade) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.getId().toString())
                .claim(JwtAuthFilter.CLAIM_PAPEL, usuario.getPapelAtivo().name())
                .issuedAt(agora)
                .expiresAt(agora.plus(validade))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private UUID mensagemComTexto(UUID chatId, String texto) {
        return jdbc.queryForObject("select id from mensagem where chat_id = ? and texto = ?", UUID.class, chatId,
                texto);
    }

    private void enviar(String token, UUID chatId, String texto, int statusEsperado) throws Exception {
        mockMvc.perform(post("/chats/{chatId}/mensagens", chatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("texto", texto))))
                .andExpect(status().is(statusEsperado));
    }

    private StompSession conectar(String token) throws Exception {
        WebSocketStompClient cliente = new WebSocketStompClient(new StandardWebSocketClient());
        cliente.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders cabecalhos = new StompHeaders();
        cabecalhos.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        StompSession sessao = cliente.connectAsync("ws://localhost:" + porta + "/ws", new WebSocketHttpHeaders(),
                cabecalhos, new StompSessionHandlerAdapter() {
                }).get(ESPERA.toSeconds(), TimeUnit.SECONDS);
        sessoes.add(sessao);
        return sessao;
    }

    private BlockingQueue<EventoChatResponse> assinarChat(StompSession sessao) throws InterruptedException {
        BlockingQueue<EventoChatResponse> recebidos = new LinkedBlockingQueue<>();
        sessao.subscribe("/user/queue/chat", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return EventoChatResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                recebidos.add((EventoChatResponse) payload);
            }
        });
        // o SUBSCRIBE é assíncrono e o broker simples não manda RECEIPT: dá tempo de ele registrar a assinatura
        Thread.sleep(300);
        return recebidos;
    }

    private static EventoChatResponse proximo(BlockingQueue<EventoChatResponse> recebidos) throws InterruptedException {
        EventoChatResponse evento = recebidos.poll(ESPERA.toMillis(), TimeUnit.MILLISECONDS);
        assertThat(evento).as("evento em tempo real").isNotNull();
        return evento;
    }

    @Test
    void dadaCandidaturaCriada_quandoEmpregadorAbre_entaoChatAbertoComCabecalhoDoCandidato() throws Exception {
        UUID chatId = candidaturaCriada();

        mockMvc.perform(get("/candidaturas/{id}/chat", candidatura.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar(empregador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(chatId.toString())))
                .andExpect(jsonPath("$.candidaturaId", is(candidatura.getId().toString())))
                .andExpect(jsonPath("$.statusCandidatura", is("ENVIADA")))
                .andExpect(jsonPath("$.outraParte.nome", is("Rafael Souza")))
                .andExpect(jsonPath("$.outraParte.papel", is("CANDIDATO")))
                .andExpect(jsonPath("$.anuncio.titulo", is("Instalar 3 tomadas")))
                .andExpect(jsonPath("$.anuncio.categoria", is(categoriaNome)))
                .andExpect(jsonPath("$.isSomenteLeitura", is(false)))
                .andExpect(jsonPath("$.motivoSomenteLeitura", nullValue()))
                .andExpect(jsonPath("$.historico.mensagens", hasSize(0)));
    }

    @Test
    void dadoEventoRepetido_quandoChega_entaoContinuaUmChatSo() {
        candidaturaCriada();
        candidaturaCriada();

        assertThat(jdbc.queryForObject("select count(*) from chat where aplicacao_id = ?", Integer.class,
                candidatura.getId())).isEqualTo(1);
    }

    @Test
    void dadasAsDuasPartesConectadas_quandoTrocamMensagens_entaoEntregaEntregueELidaChegamEmTempoReal()
            throws Exception {
        UUID chatId = candidaturaCriada();
        String tokenEmpregador = logar(empregador);
        String tokenPrestador = logar(prestador);
        BlockingQueue<EventoChatResponse> doEmpregador = assinarChat(conectar(tokenEmpregador));
        BlockingQueue<EventoChatResponse> doPrestador = assinarChat(conectar(tokenPrestador));

        enviar(tokenEmpregador, chatId, "  Fecha em R$ 320 com a troca do disjuntor?  ", 201);

        EventoChatResponse nova = proximo(doPrestador);
        assertThat(nova.tipo()).isEqualTo(TipoEventoChat.MENSAGEM_NOVA);
        assertThat(nova.chatId()).isEqualTo(chatId);
        assertThat(nova.mensagem().texto()).isEqualTo("Fecha em R$ 320 com a troca do disjuntor?");
        assertThat(nova.mensagem().remetenteId()).isEqualTo(empregador.getId());
        UUID mensagemId = nova.mensagem().id();

        mockMvc.perform(post("/chats/{chatId}/entregues", chatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPrestador))
                .andExpect(status().isNoContent());
        EventoChatResponse entregue = proximo(doEmpregador);
        assertThat(entregue.tipo()).isEqualTo(TipoEventoChat.ENTREGUE);
        assertThat(entregue.mensagemIds()).containsExactly(mensagemId);

        mockMvc.perform(post("/chats/{chatId}/lidas", chatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPrestador))
                .andExpect(status().isNoContent());
        EventoChatResponse lida = proximo(doEmpregador);
        assertThat(lida.tipo()).isEqualTo(TipoEventoChat.LIDA);
        assertThat(lida.mensagemIds()).containsExactly(mensagemId);

        assertThat(jdbc.queryForObject("select count(*) from mensagem where id = ? and entregue_em is not null "
                + "and lida_em is not null and texto = texto_exibido", Integer.class, mensagemId)).isEqualTo(1);
        assertThat(doPrestador).as("quem confirma não recebe o próprio aviso").isEmpty();
    }

    @Test
    void dadoHistorico_quandoAbre_entaoMaisNovaPrimeiroEPaginaPelaUltimaMensagemVista() throws Exception {
        UUID chatId = candidaturaCriada();
        String tokenPrestador = logar(prestador);
        enviar(tokenPrestador, chatId, "primeira", 201);
        enviar(tokenPrestador, chatId, "segunda", 201);
        enviar(tokenPrestador, chatId, "terceira", 201);

        mockMvc.perform(get("/candidaturas/{id}/chat", candidatura.getId())
                        .param("tamanho", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPrestador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outraParte.papel", is("EMPREGADOR")))
                .andExpect(jsonPath("$.outraParte.nome", is("Marina Bertoldi")))
                .andExpect(jsonPath("$.historico.mensagens", hasSize(2)))
                .andExpect(jsonPath("$.historico.mensagens[0].texto", is("terceira")))
                .andExpect(jsonPath("$.historico.mensagens[1].texto", is("segunda")))
                .andExpect(jsonPath("$.historico.temMais", is(true)));

        // chega mensagem nova enquanto o usuário rola: a página seguinte não repete nada
        enviar(tokenPrestador, chatId, "quarta", 201);
        mockMvc.perform(get("/candidaturas/{id}/chat", candidatura.getId())
                        .param("antesDe", mensagemComTexto(chatId, "segunda").toString())
                        .param("tamanho", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPrestador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historico.mensagens", hasSize(1)))
                .andExpect(jsonPath("$.historico.mensagens[0].texto", is("primeira")))
                .andExpect(jsonPath("$.historico.temMais", is(false)));

        assertThat(jdbc.queryForObject("select count(*) from perfil_empregador", Integer.class))
                .as("abrir o chat não cria o perfil de Empregador da outra parte").isZero();
    }

    @Test
    void dadoTamanhoForaDoLimite_quandoAbre_entaoValidacaoFalhou() throws Exception {
        candidaturaCriada();
        String token = logar(prestador);

        for (String tamanho : List.of("0", "101")) {
            mockMvc.perform(get("/candidaturas/{id}/chat", candidatura.getId())
                            .param("tamanho", tamanho)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        }
    }

    @Test
    void dadaCandidaturaSemChat_quandoUmaDasPartesAbre_entaoChatCriadoNaHora() throws Exception {
        // sem CandidaturaCriadaEvent: como se a reação depois do commit tivesse falhado
        String tokenTerceiro = logar(cadastrar("Curioso", "curioso@example.com", "39053344705",
                PapelUsuario.PRESTADOR));
        mockMvc.perform(get("/candidaturas/{id}/chat", candidatura.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenTerceiro))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from chat", Integer.class))
                .as("terceiro não cria chat").isZero();

        mockMvc.perform(get("/candidaturas/{id}/chat", candidatura.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar(empregador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.isSomenteLeitura", is(false)));

        candidaturaCriada(); // o evento atrasado não cria um segundo
        assertThat(jdbc.queryForObject("select count(*) from chat where aplicacao_id = ?", Integer.class,
                candidatura.getId())).isEqualTo(1);
    }

    @Test
    void dadoAnuncioCancelado_quandoEnvia_entaoRecusadoEHistoricoContinua() throws Exception {
        UUID chatId = candidaturaCriada();
        String tokenEmpregador = logar(empregador);
        enviar(tokenEmpregador, chatId, "Vou ver com o pessoal aqui e te falo.", 201);

        transacao.executeWithoutResult(status -> eventos.publishEvent(
                new AnuncioCanceladoEvent(anuncio.getId(), Instant.now())));

        mockMvc.perform(post("/chats/{chatId}/mensagens", chatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmpregador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("texto", "Ainda está de pé?"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CHAT_SOMENTE_LEITURA")));
        mockMvc.perform(get("/candidaturas/{id}/chat", candidatura.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar(prestador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSomenteLeitura", is(true)))
                .andExpect(jsonPath("$.motivoSomenteLeitura", is("ANUNCIO_CANCELADO")))
                .andExpect(jsonPath("$.historico.mensagens", hasSize(1)));
    }

    @Test
    void dadoTerceiro_quandoTentaAbrirOuEnviar_entaoChatNaoEncontrado() throws Exception {
        UUID chatId = candidaturaCriada();
        String tokenTerceiro = logar(cadastrar("Curioso", "curioso@example.com", "39053344705",
                PapelUsuario.PRESTADOR));

        mockMvc.perform(get("/candidaturas/{id}/chat", candidatura.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenTerceiro))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("CHAT_NAO_ENCONTRADO")));
        mockMvc.perform(post("/chats/{chatId}/mensagens", chatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenTerceiro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("texto", "oi"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("CHAT_NAO_ENCONTRADO")));
        mockMvc.perform(post("/chats/{chatId}/lidas", chatId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenTerceiro))
                .andExpect(status().isNotFound());
    }

    @Test
    void dadoTextoEmBrancoOuLongoDemais_quandoEnvia_entaoValidacaoFalhou() throws Exception {
        UUID chatId = candidaturaCriada();
        String token = logar(prestador);

        for (String texto : List.of("   ", "a".repeat(2001))) {
            mockMvc.perform(post("/chats/{chatId}/mensagens", chatId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("texto", texto))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        }
        assertThat(jdbc.queryForObject("select count(*) from mensagem", Integer.class)).isZero();
    }

    @Test
    void dadoAutorComContaEncerrada_quandoOutraParteAbre_entaoUsuarioExcluidoSomenteLeituraEHistoricoMantido() throws Exception {
        UUID chatId = candidaturaCriada();
        String tokenPrestador = logar(prestador);
        enviar(tokenPrestador, chatId, "Boa tarde! Vi o anúncio.", 201);

        mockMvc.perform(post("/auth/encerrar-conta")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPrestador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confirmacao", true))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/candidaturas/{id}/chat", candidatura.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar(empregador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outraParte.nome", is(OutraParte.NOME_CONTA_ENCERRADA)))
                .andExpect(jsonPath("$.outraParte.fotoUrl", nullValue()))
                .andExpect(jsonPath("$.historico.mensagens", hasSize(1)))
                .andExpect(jsonPath("$.historico.mensagens[0].texto", is("Boa tarde! Vi o anúncio.")))
                .andExpect(jsonPath("$.isSomenteLeitura", is(true)))
                .andExpect(jsonPath("$.motivoSomenteLeitura", is("CONTA_ENCERRADA")));
        enviar(logar(empregador), chatId, "Ainda está aí?", 409);
    }

    @Test
    void dadoTokenInvalido_quandoConectaNoCanal_entaoConexaoRecusada() {
        assertThatThrownBy(() -> conectar("token-invalido")).isInstanceOf(ExecutionException.class);
    }

    @Test
    void dadoTokenVencido_quandoChegaMensagemNova_entaoConexaoFechadaSemEntregar() throws Exception {
        UUID chatId = candidaturaCriada();
        StompSession sessao = conectar(tokenQueVenceEm(prestador, Duration.ofSeconds(2)));
        BlockingQueue<EventoChatResponse> doPrestador = assinarChat(sessao);

        Thread.sleep(2_500);
        enviar(logar(empregador), chatId, "Consegue amanhã cedo?", 201);

        assertThat(doPrestador.poll(ESPERA.toMillis(), TimeUnit.MILLISECONDS)).isNull();
        long limite = System.currentTimeMillis() + ESPERA.toMillis();
        while (sessao.isConnected() && System.currentTimeMillis() < limite)
            Thread.sleep(50);
        assertThat(sessao.isConnected()).as("sessão fechada depois do vencimento").isFalse();
    }
}
