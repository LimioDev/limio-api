package com.limio.api.modulos.chat.story;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
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

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta do ETI-103: {@code GET /chats} com login real e banco real (Testcontainers). Chats, candidaturas
 * e mensagens são semeados direto no banco, com data controlada, para que a ordem e a contagem de não lidas sejam
 * determinísticas.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ListarConversasStoryTest {

    private static final String SENHA = "senhaForte123";
    private static final String ROTA = "/chats";
    private static final Instant BASE = Instant.parse("2026-11-01T10:00:00Z");

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
    private JwtEncoder jwtEncoder;

    private Usuario empregador;
    private Usuario prestador;
    private UUID categoriaId;

    @BeforeEach
    void preparar() {
        limparBase();
        empregador = cadastrar("Marina Bertoldi", "marina@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        prestador = cadastrar("Rafael Souza", "rafael@example.com", "11144477735", PapelUsuario.PRESTADOR);
        categoriaId = (UUID) jdbc.queryForMap("select id from categoria where protegida").get("id");
    }

    @AfterEach
    void limpar() {
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

    /** Token válido do usuário com o papel pedido, no formato lido pelo {@link JwtAuthFilter}. */
    private String tokenComPapel(Usuario usuario, PapelUsuario papel) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.getId().toString())
                .claim(JwtAuthFilter.CLAIM_PAPEL, papel.name())
                .issuedAt(agora)
                .expiresAt(agora.plus(Duration.ofHours(1)))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private ResultActions listar(String token, String query) throws Exception {
        return mockMvc.perform(get(ROTA + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    /** Anúncio do {@code dono}, candidatura do {@code candidato} e o chat entre eles, aberto e sem mensagem. */
    private UUID novoChat(Usuario dono, Usuario candidato, String titulo, StatusAplicacao status, Instant criadoEm) {
        Anuncio anuncio = anuncioRepository.save(Anuncio.builder()
                .titulo(titulo)
                .categoriaId(categoriaId)
                .contratanteId(dono.getId())
                .descricao("Serviço combinado pelo chat")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(3)))
                .status(StatusAnuncio.PUBLICADO)
                .build());
        Aplicacao aplicacao = aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(candidato.getId())
                .status(status)
                .dataAplicacao(criadoEm)
                .build());
        return jdbc.queryForObject("""
                insert into chat (aplicacao_id, anuncio_id, contratante_id, prestador_id, criado_em)
                values (?, ?, ?, ?, ?) returning id
                """, UUID.class, aplicacao.getId(), anuncio.getId(), dono.getId(), candidato.getId(),
                Timestamp.from(criadoEm));
    }

    private void tornarSomenteLeitura(UUID chatId, String motivo) {
        jdbc.update("update chat set somente_leitura = true, motivo_somente_leitura = ?, somente_leitura_em = now() "
                + "where id = ?", motivo, chatId);
    }

    private void mensagem(UUID chatId, Usuario remetente, String texto, Instant enviadaEm, boolean lida) {
        jdbc.update("""
                insert into mensagem (chat_id, remetente_id, texto, texto_exibido, data_envio, lida_em)
                values (?, ?, ?, ?, ?, ?)
                """, chatId, remetente.getId(), texto, texto, Timestamp.from(enviadaEm),
                lida ? Timestamp.from(enviadaEm) : null);
    }

    // ------------------------------------------------------------------ cenários do card

    @Test
    void dadoEmpregador_quandoLista_entaoSoOsChatsDosSeusAnunciosComOPrestadorComoOutraParte() throws Exception {
        Usuario outroPrestador = cadastrar("Ana Lima", "ana@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Usuario outroEmpregador = cadastrar("Carlos Dias", "carlos@example.com", "86288366757", PapelUsuario.EMPREGADOR);
        UUID tomadas = novoChat(empregador, prestador, "Instalar 3 tomadas", StatusAplicacao.ENVIADA, BASE);
        mensagem(tomadas, prestador, "Posso ir amanhã", BASE.plusSeconds(60), false);
        novoChat(empregador, outroPrestador, "Pintar a sala", StatusAplicacao.ENVIADA, BASE.plusSeconds(10));
        // de outro empregador: nunca aparece na lista da Marina
        novoChat(outroEmpregador, prestador, "Trocar chuveiro", StatusAplicacao.ENVIADA, BASE);

        listar(logar(empregador), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversas.content", hasSize(2)))
                .andExpect(jsonPath("$.conversas.totalElementos", is(2)))
                .andExpect(jsonPath("$.conversas.content[0].tituloAnuncio", is("Instalar 3 tomadas")))
                .andExpect(jsonPath("$.conversas.content[0].chatId", is(tomadas.toString())))
                .andExpect(jsonPath("$.conversas.content[0].candidaturaId", notNullValue()))
                .andExpect(jsonPath("$.conversas.content[0].outraParte.nome", is("Rafael Souza")))
                .andExpect(jsonPath("$.conversas.content[0].outraParte.usuarioId", is(prestador.getId().toString())))
                .andExpect(jsonPath("$.conversas.content[0].outraParte.papel", is("CANDIDATO")))
                .andExpect(jsonPath("$.conversas.content[0].statusCandidatura", is("ENVIADA")))
                .andExpect(jsonPath("$.conversas.content[0].ultimaMensagem.texto", is("Posso ir amanhã")))
                .andExpect(jsonPath("$.conversas.content[0].ultimaMensagem.enviadaPorMim", is(false)))
                .andExpect(jsonPath("$.conversas.content[0].ultimaMensagem.enviadaEm", notNullValue()))
                .andExpect(jsonPath("$.conversas.content[0].isSomenteLeitura", is(false)))
                .andExpect(jsonPath("$.conversas.content[0].motivoSomenteLeitura", nullValue()))
                .andExpect(jsonPath("$.conversas.content[1].tituloAnuncio", is("Pintar a sala")))
                .andExpect(jsonPath("$.conversas.content[1].outraParte.nome", is("Ana Lima")));
    }

    @Test
    void dadoPrestador_quandoLista_entaoSoOsChatsDasSuasCandidaturasComOEmpregadorComoOutraParte() throws Exception {
        Usuario outroPrestador = cadastrar("Ana Lima", "ana@example.com", "39053344705", PapelUsuario.PRESTADOR);
        UUID tomadas = novoChat(empregador, prestador, "Instalar 3 tomadas", StatusAplicacao.ESCOLHIDA, BASE);
        mensagem(tomadas, empregador, "Fechado, pode vir amanhã", BASE.plusSeconds(60), false);
        // candidatura de outra pessoa ao mesmo empregador: não aparece para o Rafael
        novoChat(empregador, outroPrestador, "Pintar a sala", StatusAplicacao.ENVIADA, BASE.plusSeconds(10));

        listar(logar(prestador), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversas.content", hasSize(1)))
                .andExpect(jsonPath("$.conversas.content[0].chatId", is(tomadas.toString())))
                .andExpect(jsonPath("$.conversas.content[0].tituloAnuncio", is("Instalar 3 tomadas")))
                .andExpect(jsonPath("$.conversas.content[0].outraParte.nome", is("Marina Bertoldi")))
                .andExpect(jsonPath("$.conversas.content[0].outraParte.usuarioId", is(empregador.getId().toString())))
                .andExpect(jsonPath("$.conversas.content[0].outraParte.papel", is("EMPREGADOR")))
                .andExpect(jsonPath("$.conversas.content[0].statusCandidatura", is("ESCOLHIDA")))
                .andExpect(jsonPath("$.conversas.content[0].ultimaMensagem.texto", is("Fechado, pode vir amanhã")));
    }

    @Test
    void dadoMensagensNaoLidas_quandoLista_entaoContagemPorConversaETotalSemContarAsProprias() throws Exception {
        Usuario outroPrestador = cadastrar("Ana Lima", "ana@example.com", "39053344705", PapelUsuario.PRESTADOR);
        UUID comNaoLidas = novoChat(empregador, prestador, "Instalar 3 tomadas", StatusAplicacao.ENVIADA, BASE);
        mensagem(comNaoLidas, prestador, "Oi", BASE.plusSeconds(10), false);
        mensagem(comNaoLidas, prestador, "Consegue amanhã?", BASE.plusSeconds(20), false);
        mensagem(comNaoLidas, prestador, "Já li esta", BASE.plusSeconds(5), true);
        mensagem(comNaoLidas, empregador, "Minha resposta, que nunca conta", BASE.plusSeconds(30), false);
        UUID tudoLido = novoChat(empregador, outroPrestador, "Pintar a sala", StatusAplicacao.ENVIADA, BASE);
        mensagem(tudoLido, outroPrestador, "Combinado", BASE.plusSeconds(15), true);
        String token = logar(empregador);

        listar(token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalComNaoLidas", is(1)))
                .andExpect(jsonPath("$.conversas.content[0].chatId", is(comNaoLidas.toString())))
                .andExpect(jsonPath("$.conversas.content[0].mensagensNaoLidas", is(2)))
                .andExpect(jsonPath("$.conversas.content[0].ultimaMensagem.enviadaPorMim", is(true)))
                .andExpect(jsonPath("$.conversas.content[1].mensagensNaoLidas", is(0)));

        mockMvc.perform(post("/chats/{chatId}/lidas", comNaoLidas).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        listar(token, "")
                .andExpect(jsonPath("$.totalComNaoLidas", is(0)))
                .andExpect(jsonPath("$.conversas.content[0].mensagensNaoLidas", is(0)));
        // quem escreveu continua com a mensagem dele não lida pelo outro lado: a contagem é por usuário
        listar(logar(prestador), "")
                .andExpect(jsonPath("$.totalComNaoLidas", is(1)))
                .andExpect(jsonPath("$.conversas.content[0].mensagensNaoLidas", is(1)));
    }

    @Test
    void dadoChatsSomenteLeitura_quandoLista_entaoContinuamNaListaComStatusDaCandidaturaEMotivo() throws Exception {
        Usuario outroPrestador = cadastrar("Ana Lima", "ana@example.com", "39053344705", PapelUsuario.PRESTADOR);
        UUID recusada = novoChat(empregador, prestador, "Instalar 3 tomadas", StatusAplicacao.RECUSADA, BASE);
        mensagem(recusada, empregador, "Infelizmente escolhi outra pessoa", BASE.plusSeconds(60), false);
        tornarSomenteLeitura(recusada, "CANDIDATURA_RECUSADA");
        UUID aberta = novoChat(empregador, outroPrestador, "Pintar a sala", StatusAplicacao.ESCOLHIDA, BASE);
        mensagem(aberta, outroPrestador, "Começo segunda", BASE.plusSeconds(30), false);

        listar(logar(prestador), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversas.content", hasSize(1)))
                .andExpect(jsonPath("$.conversas.content[0].statusCandidatura", is("RECUSADA")))
                .andExpect(jsonPath("$.conversas.content[0].isSomenteLeitura", is(true)))
                .andExpect(jsonPath("$.conversas.content[0].motivoSomenteLeitura", is("CANDIDATURA_RECUSADA")))
                .andExpect(jsonPath("$.conversas.content[0].mensagensNaoLidas", is(1)))
                .andExpect(jsonPath("$.totalComNaoLidas", is(1)));

        listar(logar(empregador), "")
                .andExpect(jsonPath("$.conversas.content", hasSize(2)))
                .andExpect(jsonPath("$.conversas.content[0].tituloAnuncio", is("Instalar 3 tomadas")))
                .andExpect(jsonPath("$.conversas.content[0].isSomenteLeitura", is(true)))
                .andExpect(jsonPath("$.conversas.content[1].tituloAnuncio", is("Pintar a sala")))
                .andExpect(jsonPath("$.conversas.content[1].isSomenteLeitura", is(false)))
                .andExpect(jsonPath("$.conversas.content[1].statusCandidatura", is("ESCOLHIDA")));
    }

    @Test
    void dadoPrestadorComContaEncerrada_quandoEmpregadorLista_entaoUsuarioExcluidoSemFotoEHistoricoMantido()
            throws Exception {
        UUID chatId = novoChat(empregador, prestador, "Instalar 3 tomadas", StatusAplicacao.ENVIADA, BASE);
        mensagem(chatId, prestador, "Boa tarde! Vi o anúncio.", BASE.plusSeconds(60), false);
        mockMvc.perform(post("/auth/encerrar-conta")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar(prestador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confirmacao", true))))
                .andExpect(status().isOk());

        listar(logar(empregador), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversas.content", hasSize(1)))
                .andExpect(jsonPath("$.conversas.content[0].outraParte.nome", is(OutraParte.NOME_CONTA_ENCERRADA)))
                .andExpect(jsonPath("$.conversas.content[0].outraParte.fotoUrl", nullValue()))
                .andExpect(jsonPath("$.conversas.content[0].outraParte.usuarioId", is(prestador.getId().toString())))
                .andExpect(jsonPath("$.conversas.content[0].ultimaMensagem.texto", is("Boa tarde! Vi o anúncio.")))
                .andExpect(jsonPath("$.conversas.content[0].mensagensNaoLidas", is(1)))
                .andExpect(jsonPath("$.conversas.content[0].isSomenteLeitura", is(true)))
                .andExpect(jsonPath("$.conversas.content[0].motivoSomenteLeitura", is("CONTA_ENCERRADA")));
    }

    @Test
    void dadoEmpregadorComContaEncerrada_quandoPrestadorLista_entaoUsuarioExcluidoSemFoto() throws Exception {
        UUID chatId = novoChat(empregador, prestador, "Instalar 3 tomadas", StatusAplicacao.ESCOLHIDA, BASE);
        mensagem(chatId, empregador, "Pode começar segunda", BASE.plusSeconds(60), false);
        mockMvc.perform(post("/auth/encerrar-conta")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar(empregador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confirmacao", true))))
                .andExpect(status().isOk());

        listar(logar(prestador), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversas.content[0].outraParte.nome", is(OutraParte.NOME_CONTA_ENCERRADA)))
                .andExpect(jsonPath("$.conversas.content[0].outraParte.fotoUrl", nullValue()))
                .andExpect(jsonPath("$.conversas.content[0].outraParte.papel", is("EMPREGADOR")))
                .andExpect(jsonPath("$.conversas.content[0].ultimaMensagem.texto", is("Pode começar segunda")));
    }

    // ------------------------------------------------------------------ complementares

    @Test
    void dadoUsuarioSemConversas_quandoLista_entao200ComPaginaVaziaETotalZero() throws Exception {
        novoChat(empregador, prestador, "Instalar 3 tomadas", StatusAplicacao.ENVIADA, BASE);
        Usuario terceiro = cadastrar("Carlos Dias", "carlos@example.com", "86288366757", PapelUsuario.EMPREGADOR);

        listar(logar(terceiro), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversas.content", hasSize(0)))
                .andExpect(jsonPath("$.conversas.totalElementos", is(0)))
                .andExpect(jsonPath("$.conversas.totalPaginas", is(0)))
                .andExpect(jsonPath("$.totalComNaoLidas", is(0)));
    }

    @Test
    void dadoUsuarioComOsDoisPapeis_quandoMudaOPapelDoToken_entaoVeCadaContextoSeparado() throws Exception {
        Usuario outroEmpregador = cadastrar("Carlos Dias", "carlos@example.com", "86288366757", PapelUsuario.EMPREGADOR);
        // Marina é Empregadora num chat e Prestadora no outro
        novoChat(empregador, prestador, "Instalar 3 tomadas", StatusAplicacao.ENVIADA, BASE);
        novoChat(outroEmpregador, empregador, "Trocar chuveiro", StatusAplicacao.ENVIADA, BASE);

        listar(tokenComPapel(empregador, PapelUsuario.EMPREGADOR), "")
                .andExpect(jsonPath("$.conversas.content", hasSize(1)))
                .andExpect(jsonPath("$.conversas.content[0].tituloAnuncio", is("Instalar 3 tomadas")))
                .andExpect(jsonPath("$.conversas.content[0].outraParte.nome", is("Rafael Souza")));

        listar(tokenComPapel(empregador, PapelUsuario.PRESTADOR), "")
                .andExpect(jsonPath("$.conversas.content", hasSize(1)))
                .andExpect(jsonPath("$.conversas.content[0].tituloAnuncio", is("Trocar chuveiro")))
                .andExpect(jsonPath("$.conversas.content[0].outraParte.nome", is("Carlos Dias")));
    }

    @Test
    void dadoVariasPaginas_quandoLista_entaoPaginaETotalDeNaoLidasIndependentesDaPaginaPedida() throws Exception {
        Usuario ana = cadastrar("Ana Lima", "ana@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Usuario beto = cadastrar("Beto Alves", "beto@example.com", "86288366757", PapelUsuario.PRESTADOR);
        UUID maisAntigo = novoChat(empregador, prestador, "Instalar 3 tomadas", StatusAplicacao.ENVIADA, BASE);
        mensagem(maisAntigo, prestador, "Mais antiga, não lida", BASE.plusSeconds(10), false);
        UUID meio = novoChat(empregador, ana, "Pintar a sala", StatusAplicacao.ENVIADA, BASE);
        mensagem(meio, ana, "No meio", BASE.plusSeconds(20), true);
        UUID maisNovo = novoChat(empregador, beto, "Trocar chuveiro", StatusAplicacao.ENVIADA, BASE);
        mensagem(maisNovo, beto, "Mais nova", BASE.plusSeconds(30), true);
        String token = logar(empregador);

        listar(token, "?pagina=0&tamanho=2")
                .andExpect(jsonPath("$.conversas.content", hasSize(2)))
                .andExpect(jsonPath("$.conversas.pagina", is(0)))
                .andExpect(jsonPath("$.conversas.tamanho", is(2)))
                .andExpect(jsonPath("$.conversas.totalElementos", is(3)))
                .andExpect(jsonPath("$.conversas.totalPaginas", is(2)))
                .andExpect(jsonPath("$.conversas.content[0].chatId", is(maisNovo.toString())))
                .andExpect(jsonPath("$.conversas.content[1].chatId", is(meio.toString())))
                // a conversa não lida está na página 2, mas o total é de todas as páginas
                .andExpect(jsonPath("$.totalComNaoLidas", is(1)));

        listar(token, "?pagina=1&tamanho=2")
                .andExpect(jsonPath("$.conversas.content", hasSize(1)))
                .andExpect(jsonPath("$.conversas.pagina", is(1)))
                .andExpect(jsonPath("$.conversas.content[0].chatId", is(maisAntigo.toString())))
                .andExpect(jsonPath("$.conversas.content[0].mensagensNaoLidas", is(1)))
                .andExpect(jsonPath("$.totalComNaoLidas", is(1)));

        listar(token, "?pagina=5&tamanho=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversas.content", hasSize(0)))
                .andExpect(jsonPath("$.conversas.totalElementos", is(3)))
                .andExpect(jsonPath("$.totalComNaoLidas", is(1)));
    }

    @Test
    void dadoChatsComESemMensagem_quandoLista_entaoOrdenaPelaUltimaMensagemEChatSemMensagemPelaCriacao()
            throws Exception {
        Usuario ana = cadastrar("Ana Lima", "ana@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Usuario beto = cadastrar("Beto Alves", "beto@example.com", "86288366757", PapelUsuario.PRESTADOR);
        // criado primeiro, mas com a mensagem mais nova
        UUID ultimaMensagemMaisNova = novoChat(empregador, prestador, "A", StatusAplicacao.ENVIADA, BASE);
        mensagem(ultimaMensagemMaisNova, prestador, "antiga", BASE.plusSeconds(10), true);
        mensagem(ultimaMensagemMaisNova, prestador, "recente", BASE.plusSeconds(5000), true);
        UUID mensagemAntiga = novoChat(empregador, ana, "B", StatusAplicacao.ENVIADA, BASE.plusSeconds(100));
        mensagem(mensagemAntiga, ana, "só esta", BASE.plusSeconds(200), true);
        // sem mensagem: entra pela data de criação, entre as duas
        UUID semMensagem = novoChat(empregador, beto, "C", StatusAplicacao.ENVIADA, BASE.plusSeconds(3000));

        listar(logar(empregador), "")
                .andExpect(jsonPath("$.conversas.content", hasSize(3)))
                .andExpect(jsonPath("$.conversas.content[0].chatId", is(ultimaMensagemMaisNova.toString())))
                .andExpect(jsonPath("$.conversas.content[0].ultimaMensagem.texto", is("recente")))
                .andExpect(jsonPath("$.conversas.content[1].chatId", is(semMensagem.toString())))
                .andExpect(jsonPath("$.conversas.content[1].ultimaMensagem", nullValue()))
                .andExpect(jsonPath("$.conversas.content[1].mensagensNaoLidas", is(0)))
                .andExpect(jsonPath("$.conversas.content[2].chatId", is(mensagemAntiga.toString())));
    }

    @Test
    void dadosEstadosDeCandidatura_quandoLista_entaoCadaItemTrazOStatusDaSuaCandidatura() throws Exception {
        Usuario ana = cadastrar("Ana Lima", "ana@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Usuario beto = cadastrar("Beto Alves", "beto@example.com", "86288366757", PapelUsuario.PRESTADOR);
        novoChat(empregador, prestador, "A", StatusAplicacao.ENVIADA, BASE.plusSeconds(3));
        novoChat(empregador, ana, "B", StatusAplicacao.ESCOLHIDA, BASE.plusSeconds(2));
        UUID encerrada = novoChat(empregador, beto, "C", StatusAplicacao.ENCERRADA, BASE.plusSeconds(1));
        tornarSomenteLeitura(encerrada, "OUTRO_PRESTADOR_ESCOLHIDO");

        listar(logar(empregador), "")
                .andExpect(jsonPath("$.conversas.content[0].statusCandidatura", is("ENVIADA")))
                .andExpect(jsonPath("$.conversas.content[1].statusCandidatura", is("ESCOLHIDA")))
                .andExpect(jsonPath("$.conversas.content[2].statusCandidatura", is("ENCERRADA")))
                .andExpect(jsonPath("$.conversas.content[2].isSomenteLeitura", is(true)));
    }

    @Test
    void dadosParametrosDePaginacaoInvalidos_quandoLista_entao400ValidacaoFalhou() throws Exception {
        String token = logar(empregador);
        for (String query : new String[] { "?pagina=-1", "?tamanho=0", "?tamanho=51", "?tamanho=abc" }) {
            listar(token, query)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        }
    }

    @Test
    void dadoSemToken_quandoLista_entao401() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }
}
