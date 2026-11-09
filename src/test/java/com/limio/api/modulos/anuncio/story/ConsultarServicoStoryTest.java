package com.limio.api.modulos.anuncio.story;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;

import com.limio.api.TestcontainersConfiguration;
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
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatRepository;
import com.limio.api.modulos.chat.mensagem.Mensagem;
import com.limio.api.modulos.chat.mensagem.MensagemRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do UC "Consultar o serviço combinado" (ETI-86). Anúncio,
 * candidatura, chat e mensagem nascem direto pelo repository: não existe usecase de "escolher candidato"
 * (ETI-69/ETI-70) nem de reagendamento (ETI-99/ETI-100) ainda.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConsultarServicoStoryTest {

    private static final String SENHA = "senhaForte123";

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
    private ReagendamentoRepository reagendamentoRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private MensagemRepository mensagemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario empregador;
    private Usuario prestador;
    private Anuncio anuncio;
    private Aplicacao candidatura;

    @BeforeEach
    void prepararServicoCombinado() {
        limparBase();
        empregador = cadastrar("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        prestador = cadastrar("Rafael Souza", "rafael@example.com", "11144477735", PapelUsuario.PRESTADOR);
        Map<String, Object> categoria = jdbc.queryForMap("select id from categoria where protegida");
        anuncio = anuncioRepository.save(Anuncio.builder()
                .titulo("Instalar 3 tomadas")
                .categoriaId((UUID) categoria.get("id"))
                .contratanteId(empregador.getId())
                .descricao("Trocar três tomadas da cozinha")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(5)))
                .bairroRegiaoSnapshot("Pinheiros")
                .enderecoCompletoSnapshot("Rua dos Pinheiros, 100, Pinheiros, São Paulo - SP")
                .status(StatusAnuncio.EM_ANDAMENTO)
                .build());
        candidatura = aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(prestador.getId())
                .status(StatusAplicacao.AGUARDANDO_VALIDACAO)
                .dataAplicacao(Instant.now().minus(Duration.ofDays(6)))
                .escolhidaEm(Instant.now().minus(Duration.ofDays(5)))
                .build());
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private void limparBase() {
        mensagemRepository.deleteAll();
        chatRepository.deleteAll();
        reagendamentoRepository.deleteAll();
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
        jdbc.execute("delete from sessao");
        jdbc.execute("delete from tentativa_login");
        usuarioRepository.deleteAll();
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

    private String logar(String email) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, SENHA, "celular"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    private ResultActions consultar(String token, UUID anuncioId) throws Exception {
        return mockMvc.perform(get("/anuncios/{id}/servico", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void dadoEmpregadorDono_quandoConsulta_entao200ComOPrestadorComoOutraParte() throws Exception {
        String token = logar("maria@example.com");

        consultar(token, anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anuncioId", is(anuncio.getId().toString())))
                .andExpect(jsonPath("$.status", is("EM_ANDAMENTO")))
                .andExpect(jsonPath("$.enderecoCompleto", is("Rua dos Pinheiros, 100, Pinheiros, São Paulo - SP")))
                .andExpect(jsonPath("$.bairroRegiao", is("Pinheiros")))
                .andExpect(jsonPath("$.outraParte.nome", is("Rafael Souza")))
                .andExpect(jsonPath("$.outraParte.telefone", is("+5511999998888")))
                .andExpect(jsonPath("$.outraParte.gold", is(false)))
                .andExpect(jsonPath("$.escolhidaEm", notNullValue()))
                .andExpect(jsonPath("$.reagendamentosAceitos", is(0)))
                .andExpect(jsonPath("$.limiteReagendamentos", is(2)))
                .andExpect(jsonPath("$.propostaPendente", nullValue()))
                .andExpect(jsonPath("$.chatId", nullValue()))
                .andExpect(jsonPath("$.mensagensNaoLidas", is(0)));
    }

    @Test
    void dadoPrestadorEscolhido_quandoConsulta_entao200ComOEmpregadorSemGold() throws Exception {
        String token = logar("rafael@example.com");

        consultar(token, anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outraParte.nome", is("Maria da Silva")))
                .andExpect(jsonPath("$.outraParte.telefone", is("+5511999998888")))
                .andExpect(jsonPath("$.outraParte.gold", nullValue()));
    }

    @Test
    void dadoPrestadorNaoEscolhido_quandoConsulta_entao404() throws Exception {
        cadastrar("João Pereira", "joao@example.com", "15350946056", PapelUsuario.PRESTADOR);
        String token = logar("joao@example.com");

        consultar(token, anuncio.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoAnuncioInexistente_quandoConsulta_entao404() throws Exception {
        String token = logar("maria@example.com");

        consultar(token, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoSemToken_quandoConsulta_entao401() throws Exception {
        mockMvc.perform(get("/anuncios/{id}/servico", anuncio.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }

    @Test
    void dadoPropostaDeReagendamentoPendente_quandoConsulta_entaoTrazNaResposta() throws Exception {
        Instant dataProposta = anuncio.getDataHoraPretendida().plus(Duration.ofDays(2));
        reagendamentoRepository.save(Reagendamento.builder()
                .anuncioId(anuncio.getId())
                .dataAnterior(anuncio.getDataHoraPretendida())
                .dataProposta(dataProposta)
                .status(StatusReagendamento.PENDENTE)
                .justificativa("Imprevisto de agenda")
                .build());
        String token = logar("maria@example.com");

        consultar(token, anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostaPendente.justificativa", is("Imprevisto de agenda")));
    }

    @Test
    void dadoServicoJaCancelado_quandoPrestadorEscolhidoConsulta_entaoAindaAcessaOHistorico() throws Exception {
        anuncio.setStatus(StatusAnuncio.ENCERRADO);
        anuncio.setMotivoEncerramento("Cancelado pelo Empregador");
        anuncioRepository.save(anuncio);
        String token = logar("rafael@example.com");

        consultar(token, anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ENCERRADO")))
                .andExpect(jsonPath("$.outraParte.nome", is("Maria da Silva")));
    }

    @Test
    void dadoChatComMensagemNaoLida_quandoEmpregadorConsulta_entaoTrazChatIdEContagem() throws Exception {
        Chat chat = chatRepository.save(Chat.builder()
                .aplicacaoId(candidatura.getId())
                .anuncioId(anuncio.getId())
                .contratanteId(empregador.getId())
                .prestadorId(prestador.getId())
                .build());
        mensagemRepository.save(Mensagem.enviada(chat.getId(), prestador.getId(), "Bom dia, já estou chegando",
                Instant.now()));
        mensagemRepository.save(Mensagem.enviada(chat.getId(), prestador.getId(), "Confirma o endereço?",
                Instant.now()));
        String token = logar("maria@example.com");

        consultar(token, anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatId", is(chat.getId().toString())))
                .andExpect(jsonPath("$.mensagensNaoLidas", is(2)));
    }
}
