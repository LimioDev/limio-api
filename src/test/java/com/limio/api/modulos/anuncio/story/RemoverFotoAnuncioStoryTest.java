package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaRepository;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.sessao.SessaoRepository;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) da remoção de foto do anúncio (UC19, ETI-61). Como em
 * {@code CancelarAnuncioStoryTest}, anúncios, fotos e candidaturas nascem direto pelo repository. O storage é
 * mockado: o que se confere é o que o usecase manda apagar, e quando.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RemoverFotoAnuncioStoryTest {

    private static final String SENHA = "senhaForte123";

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MidiaRepository midiaRepository;

    @MockitoBean
    private ArmazenamentoService armazenamentoService;

    @BeforeEach
    void limparBase() {
        midiaRepository.deleteAll();
        // Sobra de outros stories do mesmo banco: prende o delete de usuario por FK.
        jdbc.execute("delete from verificacao_contato");
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
        sessaoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private Usuario empregador(String nome, String email, String cpf) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nome)
                .email(email)
                .telefone("11988887777")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(30))
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

    private UUID categoriaAtiva() {
        return jdbc.queryForObject("select id from categoria where protegida", UUID.class);
    }

    private Anuncio anuncio(UUID contratanteId, StatusAnuncio status) {
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(contratanteId)
                .titulo("Instalar 3 tomadas")
                .categoriaId(categoriaAtiva())
                .descricao("Instalar 3 tomadas novas na sala e trocar o disjuntor")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(5)))
                .bairroRegiaoSnapshot("Pinheiros")
                .enderecoCompletoSnapshot("Rua dos Pinheiros, 100, Pinheiros, São Paulo - SP")
                .tamanhoLocal("Sala de 20m²")
                .status(status)
                .build());
    }

    private Aplicacao aplicacao(UUID anuncioId, StatusAplicacao status) {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(UUID.randomUUID())
                .status(status)
                .dataAplicacao(Instant.now())
                .build();
        if (status == StatusAplicacao.ESCOLHIDA) {
            aplicacao.setEscolhidaEm(Instant.now());
        }
        return aplicacaoRepository.save(aplicacao);
    }

    private Midia foto(UUID donoId, UUID anuncioId, String chave) {
        return midiaRepository.save(Midia.fotoAnuncio(donoId, anuncioId, chave, "image/jpeg", 10, 10, 100));
    }

    private ResultActions remover(String token, UUID anuncioId, UUID fotoId) throws Exception {
        return mockMvc.perform(delete("/anuncios/{id}/fotos/{fotoId}", anuncioId, fotoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void assertFotoAtiva(UUID fotoId) {
        assertThat(midiaRepository.findById(fotoId).orElseThrow().getExpurgoEm()).isNull();
    }

    @Test
    void dadoFotoDoAnuncioPublicado_quandoRemove_entao204ExpurgaEApagaOObjetoDoStorage() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Midia removida = foto(maria.getId(), anuncio.getId(), "fotos-anuncio/a/1.jpg");
        Midia mantida = foto(maria.getId(), anuncio.getId(), "fotos-anuncio/a/2.jpg");
        String token = logar("maria@example.com");

        remover(token, anuncio.getId(), removida.getId())
                .andExpect(status().isNoContent());

        assertThat(midiaRepository.findById(removida.getId()).orElseThrow().getExpurgoEm()).isNotNull();
        assertFotoAtiva(mantida.getId());
        verify(armazenamentoService).remover("fotos-anuncio/a/1.jpg");
        verify(armazenamentoService, never()).remover("fotos-anuncio/a/2.jpg");
    }

    @Test
    void dadoFotoJaRemovida_quandoRemoveDeNovo_entao404() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Midia midia = foto(maria.getId(), anuncio.getId(), "fotos-anuncio/a/1.jpg");
        String token = logar("maria@example.com");
        remover(token, anuncio.getId(), midia.getId()).andExpect(status().isNoContent());

        remover(token, anuncio.getId(), midia.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ENTIDADE_NAO_ENCONTRADA")));
    }

    @Test
    void dadoFotoDeOutroAnuncioDoMesmoDono_quandoRemovePeloAnuncioErrado_entao404() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Anuncio outro = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Midia doOutro = foto(maria.getId(), outro.getId(), "fotos-anuncio/b/1.jpg");

        remover(logar("maria@example.com"), anuncio.getId(), doOutro.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ENTIDADE_NAO_ENCONTRADA")));

        assertFotoAtiva(doOutro.getId());
        verify(armazenamentoService, never()).remover(anyString());
    }

    @Test
    void dadoCandidaturaEscolhida_quandoRemove_entao409EFotoIntacta() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Midia midia = foto(maria.getId(), anuncio.getId(), "fotos-anuncio/a/1.jpg");
        aplicacao(anuncio.getId(), StatusAplicacao.ESCOLHIDA);

        remover(logar("maria@example.com"), anuncio.getId(), midia.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_ESTADO_INVALIDO")));

        assertFotoAtiva(midia.getId());
        verify(armazenamentoService, never()).remover(anyString());
    }

    @Test
    void dadoFotoDeAnuncioDeOutroEmpregador_quandoRemove_entao404() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Usuario joao = empregador("João Pereira", "joao@example.com", "11144477735");
        Anuncio anuncioDeJoao = anuncio(joao.getId(), StatusAnuncio.PUBLICADO);
        Midia midia = foto(joao.getId(), anuncioDeJoao.getId(), "fotos-anuncio/a/1.jpg");

        remover(logar("maria@example.com"), anuncioDeJoao.getId(), midia.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_NAO_ENCONTRADO")));

        assertFotoAtiva(midia.getId());
        verify(armazenamentoService, never()).remover(anyString());
        assertThat(maria.getId()).isNotEqualTo(joao.getId());
    }

    @Test
    void dadoSemToken_quandoRemove_entao401() throws Exception {
        mockMvc.perform(delete("/anuncios/{id}/fotos/{fotoId}", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }
}
