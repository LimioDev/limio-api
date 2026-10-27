package com.limio.api.modulos.anuncio.story;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do TICKET-0052 — painel "Início" do Empregador. Não
 * existe usecase de "criar anúncio" ainda: anúncios e candidaturas nascem direto pelo repository, igual
 * {@code EncerrarAnunciosEAplicacoesStoryTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConsultarPainelEmpregadorStoryTest {

    private static final String ROTA = "/anuncios/painel-empregador";
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
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void limparBase() {
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from sessao");
        jdbc.execute("delete from tentativa_login");
        usuarioRepository.deleteAll();
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private Usuario cadastrar(String nomeCompleto, String email, String cpf) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nomeCompleto)
                .email(email)
                .telefone("11999998888")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(25))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(PapelUsuario.EMPREGADOR)
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

    private ResultActions consultar(String token) throws Exception {
        return mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Anuncio anuncio(UUID contratanteId, String descricao, StatusAnuncio status, Duration daquiA) {
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(contratanteId)
                .categoriaId(UUID.randomUUID()) // sem FK pra categoria (ADR-0001 §1.6): qualquer id serve aqui
                .descricao(descricao)
                .dataHoraPretendida(Instant.now().plus(daquiA))
                .bairroRegiaoSnapshot("Pinheiros")
                .status(status)
                .build());
    }

    private void aplicacao(UUID anuncioId, UUID prestadorId, StatusAplicacao status) {
        aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(prestadorId)
                .status(status)
                .dataAplicacao(Instant.now())
                .build());
    }

    @Test
    void dadoEmpregadorSemAnuncios_quandoConsultaPainel_entaoListaVaziaEContadoresZerados() throws Exception {
        cadastrar("Maria da Silva", "maria@example.com", "52998224725");

        consultar(logar("maria@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("Maria da Silva")))
                .andExpect(jsonPath("$.fotoUrl", nullValue()))
                .andExpect(jsonPath("$.fotoExpiraEm", nullValue()))
                .andExpect(jsonPath("$.anunciosAbertos", is(0)))
                .andExpect(jsonPath("$.novosCandidatos", is(0)))
                .andExpect(jsonPath("$.anuncios", hasSize(0)));
    }

    @Test
    void dadoAnunciosComCandidatosEEscolhido_quandoConsultaPainel_entaoResumeSoOsAbertosPorData() throws Exception {
        Usuario maria = cadastrar("Maria da Silva", "maria@example.com", "52998224725");
        Usuario rafael = cadastrar("Rafael Souza", "rafael@example.com", "11144477735");
        Usuario outro = cadastrar("Ana Lima", "ana@example.com", "10433218100");

        Anuncio comCandidatos = anuncio(maria.getId(), "Faxina pós-obra", StatusAnuncio.PUBLICADO, Duration.ofDays(5));
        aplicacao(comCandidatos.getId(), rafael.getId(), StatusAplicacao.ENVIADA);
        aplicacao(comCandidatos.getId(), outro.getId(), StatusAplicacao.ENVIADA);
        aplicacao(comCandidatos.getId(), UUID.randomUUID(), StatusAplicacao.RECUSADA);

        Anuncio combinado = anuncio(maria.getId(), "Passar roupa", StatusAnuncio.PUBLICADO, Duration.ofDays(2));
        aplicacao(combinado.getId(), rafael.getId(), StatusAplicacao.ESCOLHIDA);

        anuncio(maria.getId(), "Limpeza concluída", StatusAnuncio.CONCLUIDO, Duration.ofDays(1));
        anuncio(maria.getId(), "Limpeza encerrada", StatusAnuncio.ENCERRADO, Duration.ofDays(1));

        consultar(logar("maria@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anunciosAbertos", is(2)))
                .andExpect(jsonPath("$.novosCandidatos", is(2)))
                .andExpect(jsonPath("$.anuncios[*].descricao", contains("Passar roupa", "Faxina pós-obra")))
                .andExpect(jsonPath("$.anuncios[0].prestadorEscolhido", is("Rafael S.")))
                .andExpect(jsonPath("$.anuncios[0].candidatos", is(0)))
                .andExpect(jsonPath("$.anuncios[1].prestadorEscolhido", nullValue()))
                .andExpect(jsonPath("$.anuncios[1].candidatos", is(2)))
                .andExpect(jsonPath("$.anuncios[1].id", is(comCandidatos.getId().toString())))
                .andExpect(jsonPath("$.anuncios[1].bairro", is("Pinheiros")))
                .andExpect(jsonPath("$.anuncios[1].status", is("PUBLICADO")))
                .andExpect(jsonPath("$.anuncios[1].fotoUrl", nullValue()));
    }

    @Test
    void dadoAnunciosDeOutroEmpregador_quandoConsultaPainel_entaoNaoAparecem() throws Exception {
        cadastrar("Maria da Silva", "maria@example.com", "52998224725");
        Usuario joao = cadastrar("João Pereira", "joao@example.com", "11144477735");
        anuncio(joao.getId(), "Faxina do João", StatusAnuncio.PUBLICADO, Duration.ofDays(3));

        consultar(logar("maria@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anunciosAbertos", is(0)))
                .andExpect(jsonPath("$.anuncios", hasSize(0)));
    }

    @Test
    void dadoContaEncerrada_quandoConsultaPainel_entao404() throws Exception {
        cadastrar("Maria da Silva", "maria@example.com", "52998224725");
        String token = logar("maria@example.com");
        jdbc.update("update usuario set anonimizado_em = now() where email = ?", "maria@example.com");

        consultar(token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("PERFIL_EMPREGADOR_NAO_ENCONTRADO")));
    }

    @Test
    void dadoSemToken_quandoConsultaPainel_entao401() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }
}
