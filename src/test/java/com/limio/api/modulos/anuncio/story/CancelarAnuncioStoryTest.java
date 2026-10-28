package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.AnuncioCanceladoEvent;
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
 * BDD ponta a ponta (controller -> banco real) do UC "Cancelar anúncio antes da escolha" (ETI-63). Não
 * existe usecase de "criar anúncio" ainda: anúncios e candidaturas nascem direto pelo repository, igual
 * {@code ConsultarPainelEmpregadorStoryTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class CancelarAnuncioStoryTest {

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
    private ApplicationEvents eventos;

    @BeforeEach
    void limparBase() {
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

    private Anuncio anuncio(UUID contratanteId, StatusAnuncio status) {
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(contratanteId)
                .titulo("Instalar 3 tomadas")
                .descricao("Instalar 3 tomadas novas na sala e trocar o disjuntor")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(5)))
                .bairroRegiaoSnapshot("Pinheiros")
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

    private ResultActions cancelar(String token, UUID anuncioId) throws Exception {
        return mockMvc.perform(post("/anuncios/{anuncioId}/cancelar", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void dadoAnuncioComCandidatos_quandoCancela_entao200EEncerraTudoEPublicaEvento() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Aplicacao enviada1 = aplicacao(anuncio.getId(), StatusAplicacao.ENVIADA);
        Aplicacao enviada2 = aplicacao(anuncio.getId(), StatusAplicacao.ENVIADA);
        Aplicacao recusada = aplicacao(anuncio.getId(), StatusAplicacao.RECUSADA);

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk());

        Anuncio anuncioAtualizado = anuncioRepository.findById(anuncio.getId()).orElseThrow();
        assertThat(anuncioAtualizado.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(anuncioAtualizado.getEncerradoEm()).isNotNull();
        assertThat(anuncioAtualizado.getMotivoEncerramento()).isEqualTo("Cancelado pelo Empregador");

        assertThat(aplicacaoRepository.findById(enviada1.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(aplicacaoRepository.findById(enviada2.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAplicacao.ENCERRADA);
        // RECUSADA não é "aberta": continua RECUSADA, como o card pede.
        assertThat(aplicacaoRepository.findById(recusada.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAplicacao.RECUSADA);

        List<AnuncioCanceladoEvent> publicados = eventos.stream(AnuncioCanceladoEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).anuncioId()).isEqualTo(anuncio.getId());
    }

    @Test
    void dadoAnuncioSemCandidatos_quandoCancela_entao200() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk());

        assertThat(anuncioRepository.findById(anuncio.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAnuncio.ENCERRADO);
    }

    @Test
    void dadoAnuncioPublicadoComCandidaturaEscolhida_quandoCancela_entao409() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        aplicacao(anuncio.getId(), StatusAplicacao.ESCOLHIDA);

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_ESTADO_INVALIDO")));

        assertThat(anuncioRepository.findById(anuncio.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAnuncio.PUBLICADO);
    }

    @Test
    void dadoAnuncioJaConcluido_quandoCancela_entao409() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.CONCLUIDO);

        cancelar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_ESTADO_INVALIDO")));
    }

    @Test
    void dadoAnuncioDeOutroEmpregador_quandoCancela_entao404() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Usuario joao = empregador("João Pereira", "joao@example.com", "11144477735");
        Anuncio anuncioDeJoao = anuncio(joao.getId(), StatusAnuncio.PUBLICADO);

        cancelar(logar("maria@example.com"), anuncioDeJoao.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_NAO_ENCONTRADO")));

        assertThat(anuncioRepository.findById(anuncioDeJoao.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAnuncio.PUBLICADO);
    }

    @Test
    void dadoAnuncioInexistente_quandoCancela_entao404() throws Exception {
        empregador("Maria da Silva", "maria@example.com", "52998224725");

        cancelar(logar("maria@example.com"), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoSemToken_quandoCancela_entao401() throws Exception {
        mockMvc.perform(post("/anuncios/{anuncioId}/cancelar", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }
}
