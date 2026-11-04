package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import com.limio.api.comum.eventos.ServicoConcluidoEvent;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.ModoValidacao;
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
 * BDD ponta a ponta (controller -> banco real) do UC "Validar o serviço" (ETI-92). A escolha do Prestador
 * nasce pelo repository, como em {@code IniciarServicoStoryTest} (ETI-88): ainda não existe usecase próprio.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class ValidarServicoStoryTest {

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

    private Usuario maria;
    private Usuario carlos;

    @BeforeEach
    void prepararBase() {
        limparBase();
        maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        carlos = usuario("Carlos Souza", "carlos@example.com", "11144477735", PapelUsuario.PRESTADOR);
    }

    @AfterEach
    void limparBase() {
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
        sessaoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

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

    private Anuncio anuncio(StatusAnuncio status) {
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(maria.getId())
                .titulo("Instalar 3 tomadas")
                .categoriaId(UUID.randomUUID()) // sem FK pra categoria (ADR-0001 §1.6): qualquer id serve aqui
                .descricao("Instalar 3 tomadas novas na sala e trocar o disjuntor")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(5)))
                .bairroRegiaoSnapshot("Pinheiros")
                .status(status)
                .build());
    }

    private Anuncio anuncioComCarlosEscolhido(StatusAnuncio status) {
        Anuncio anuncio = anuncio(status);
        aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(carlos.getId())
                .status(StatusAplicacao.ESCOLHIDA)
                .dataAplicacao(Instant.now())
                .escolhidaEm(Instant.now())
                .build());
        return anuncio;
    }

    private ResultActions validar(String token, UUID anuncioId, ModoValidacao modo) throws Exception {
        return mockMvc.perform(post("/anuncios/{anuncioId}/validar", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("modo", modo.name()))));
    }

    private ResultActions consultarValidacao(String token, UUID anuncioId) throws Exception {
        return mockMvc.perform(get("/anuncios/{anuncioId}/validacao", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Anuncio recarregar(Anuncio anuncio) {
        return anuncioRepository.findById(anuncio.getId()).orElseThrow();
    }

    @Test
    void dadoEmpregadorDono_quandoValidaPresencialDePublicado_entao200ConcluidoEPublicaEvento() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.PUBLICADO);

        validar(logar("maria@example.com"), anuncio.getId(), ModoValidacao.PRESENCIAL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONCLUIDO")))
                .andExpect(jsonPath("$.validadoEm", notNullValue()));

        Anuncio atualizado = recarregar(anuncio);
        assertThat(atualizado.getStatus()).isEqualTo(StatusAnuncio.CONCLUIDO);
        assertThat(atualizado.getValidadoEm()).isNotNull();

        List<ServicoConcluidoEvent> publicados = eventos.stream(ServicoConcluidoEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).anuncioId()).isEqualTo(anuncio.getId());
        assertThat(publicados.get(0).contratanteId()).isEqualTo(maria.getId());
        assertThat(publicados.get(0).prestadorId()).isEqualTo(carlos.getId());
    }

    @Test
    void dadoEmpregadorDono_quandoValidaPresencialDeEmAndamento_entao200Concluido() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.EM_ANDAMENTO);

        validar(logar("maria@example.com"), anuncio.getId(), ModoValidacao.PRESENCIAL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONCLUIDO")));
    }

    @Test
    void dadoEmpregadorDono_quandoValidaPelaFotoDeAguardandoValidacao_entao200Concluido() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.AGUARDANDO_VALIDACAO);

        validar(logar("maria@example.com"), anuncio.getId(), ModoValidacao.PELA_FOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONCLUIDO")));
    }

    @Test
    void dadoEmpregadorDono_quandoValidaPelaFotoSemCheckout_entao409ValidacaoSemCheckout() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.PUBLICADO);

        validar(logar("maria@example.com"), anuncio.getId(), ModoValidacao.PELA_FOTO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_SEM_CHECKOUT")));

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
    }

    @Test
    void dadoServicoJaConcluido_quandoValidaPelaFoto_entao409EstadoInvalidoNaoSemCheckout() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.PUBLICADO);
        String token = logar("maria@example.com");
        validar(token, anuncio.getId(), ModoValidacao.PRESENCIAL).andExpect(status().isOk());

        validar(token, anuncio.getId(), ModoValidacao.PELA_FOTO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));
    }

    @Test
    void dadoEmpregadorDono_quandoValidaPresencialDeAguardandoValidacao_entao409EstadoInvalido() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.AGUARDANDO_VALIDACAO);

        validar(logar("maria@example.com"), anuncio.getId(), ModoValidacao.PRESENCIAL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));
    }

    @Test
    void dadoServicoJaConcluido_quandoValidaDeNovo_entao409EMantemAPrimeiraValidacao() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.PUBLICADO);
        String token = logar("maria@example.com");
        validar(token, anuncio.getId(), ModoValidacao.PRESENCIAL).andExpect(status().isOk());
        Instant primeiraValidacao = recarregar(anuncio).getValidadoEm();

        validar(token, anuncio.getId(), ModoValidacao.PRESENCIAL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));

        assertThat(recarregar(anuncio).getValidadoEm()).isEqualTo(primeiraValidacao);
        assertThat(eventos.stream(ServicoConcluidoEvent.class)).hasSize(1);
    }

    @Test
    void dadoCliqueDuplo_quandoValidaEmParalelo_entaoSoUmaRequisicaoPassa() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.PUBLICADO);
        String token = logar("maria@example.com");
        Callable<Integer> tarefa = () -> validar(token, anuncio.getId(), ModoValidacao.PRESENCIAL)
                .andReturn().getResponse().getStatus();

        List<Integer> status = emParalelo(List.of(tarefa, tarefa));

        assertThat(status).containsExactlyInAnyOrder(200, 409);
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

    @Test
    void dadoQuemNaoEOEmpregadorDono_quandoValida_entao404() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.PUBLICADO);

        validar(logar("carlos@example.com"), anuncio.getId(), ModoValidacao.PRESENCIAL)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
    }

    @Test
    void dadoAnuncioInexistente_quandoValida_entao404() throws Exception {
        validar(logar("maria@example.com"), UUID.randomUUID(), ModoValidacao.PRESENCIAL)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoSemToken_quandoValida_entao401() throws Exception {
        mockMvc.perform(post("/anuncios/{anuncioId}/validar", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("modo", "PRESENCIAL"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }

    @Test
    void dadoSemModoNoCorpo_quandoValida_entao400() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.PUBLICADO);

        mockMvc.perform(post("/anuncios/{anuncioId}/validar", anuncio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar("maria@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoEmpregadorDono_quandoConsultaValidacaoSemCheckout_entao200SemFotos() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.PUBLICADO);

        consultarValidacao(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotosCheckout", empty()))
                .andExpect(jsonPath("$.checkoutEm", nullValue()))
                .andExpect(jsonPath("$.nomePrestador", is("Carlos Souza")));
    }

    @Test
    void dadoQuemNaoEOEmpregadorDono_quandoConsultaValidacao_entao404() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.PUBLICADO);

        consultarValidacao(logar("carlos@example.com"), anuncio.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
    }
}
