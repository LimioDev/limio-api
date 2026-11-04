package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
import com.limio.api.comum.eventos.ServicoIniciadoEvent;
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
import com.limio.api.modulos.auth.sessao.SessaoRepository;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do UC "Iniciar o serviço" (ETI-88). A escolha do Prestador
 * e a proposta de reagendamento ainda não têm usecase: candidaturas e propostas nascem pelo repository.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class IniciarServicoStoryTest {

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
    private ReagendamentoRepository reagendamentoRepository;

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
        reagendamentoRepository.deleteAll();
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

    private void aplicacao(UUID anuncioId, UUID prestadorId, StatusAplicacao status) {
        aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(prestadorId)
                .status(status)
                .dataAplicacao(Instant.now())
                .build());
    }

    private Anuncio anuncioComCarlosEscolhido() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        aplicacao(anuncio.getId(), carlos.getId(), StatusAplicacao.ESCOLHIDA);
        return anuncio;
    }

    private Reagendamento reagendamento(UUID anuncioId, StatusReagendamento status) {
        return reagendamentoRepository.save(Reagendamento.builder()
                .anuncioId(anuncioId)
                .dataAnterior(Instant.now().plus(Duration.ofDays(5)))
                .dataProposta(Instant.now().plus(Duration.ofDays(6)))
                .status(status)
                .build());
    }

    private ResultActions iniciar(String token, UUID anuncioId) throws Exception {
        return mockMvc.perform(post("/anuncios/{anuncioId}/iniciar", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Anuncio recarregar(Anuncio anuncio) {
        return anuncioRepository.findById(anuncio.getId()).orElseThrow();
    }

    @Test
    void dadoPrestadorEscolhido_quandoInicia_entao200EmAndamentoEPublicaEvento() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido();

        iniciar(logar("carlos@example.com"), anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("EM_ANDAMENTO")))
                .andExpect(jsonPath("$.iniciadoEm", notNullValue()));

        Anuncio atualizado = recarregar(anuncio);
        assertThat(atualizado.getStatus()).isEqualTo(StatusAnuncio.EM_ANDAMENTO);
        assertThat(atualizado.getIniciadoEm()).isNotNull();

        List<ServicoIniciadoEvent> publicados = eventos.stream(ServicoIniciadoEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).anuncioId()).isEqualTo(anuncio.getId());
        assertThat(publicados.get(0).contratanteId()).isEqualTo(maria.getId());
        assertThat(publicados.get(0).prestadorId()).isEqualTo(carlos.getId());
    }

    @Test
    void dadoPrestadorNaoEscolhido_quandoInicia_entao404ENaoMudaOAnuncio() throws Exception {
        Usuario joao = usuario("João Pereira", "joao@example.com", "39053344705", PapelUsuario.PRESTADOR);
        Anuncio anuncio = anuncioComCarlosEscolhido();
        aplicacao(anuncio.getId(), joao.getId(), StatusAplicacao.RECUSADA);

        iniciar(logar("joao@example.com"), anuncio.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(eventos.stream(ServicoIniciadoEvent.class)).isEmpty();
    }

    @Test
    void dadoEmpregadorDono_quandoInicia_entao404() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido();

        iniciar(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
    }

    @Test
    void dadoServicoJaEmAndamento_quandoInicia_entao409EMantemOPrimeiroInicio() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido();
        String token = logar("carlos@example.com");
        iniciar(token, anuncio.getId()).andExpect(status().isOk());
        Instant primeiroInicio = recarregar(anuncio).getIniciadoEm();

        iniciar(token, anuncio.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));

        assertThat(recarregar(anuncio).getIniciadoEm()).isEqualTo(primeiroInicio);
        assertThat(eventos.stream(ServicoIniciadoEvent.class)).hasSize(1);
    }

    @Test
    void dadoPropostaPendente_quandoInicia_entaoEncerraSemEfeitoENaoTocaNaRespondida() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido();
        Reagendamento pendente = reagendamento(anuncio.getId(), StatusReagendamento.PENDENTE);
        Reagendamento recusada = reagendamento(anuncio.getId(), StatusReagendamento.RECUSADO);

        iniciar(logar("carlos@example.com"), anuncio.getId())
                .andExpect(status().isOk());

        Reagendamento encerrada = reagendamentoRepository.findById(pendente.getId()).orElseThrow();
        assertThat(encerrada.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
        assertThat(encerrada.getRespondidoEm()).isNotNull();
        assertThat(reagendamentoRepository.findById(recusada.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusReagendamento.RECUSADO);
    }

    @Test
    void dadoServicoIniciado_quandoEmpregadorCancela_entao409() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido();
        iniciar(logar("carlos@example.com"), anuncio.getId()).andExpect(status().isOk());

        mockMvc.perform(post("/anuncios/{anuncioId}/cancelar", anuncio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar("maria@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_ESTADO_INVALIDO")));

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.EM_ANDAMENTO);
    }

    @Test
    void dadoCliqueDuplo_quandoIniciaEmParalelo_entaoSoUmaRequisicaoPassa() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido();
        String token = logar("carlos@example.com");
        Callable<Integer> tarefa = () -> iniciar(token, anuncio.getId()).andReturn().getResponse().getStatus();

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
    void dadoAnuncioInexistente_quandoInicia_entao404() throws Exception {
        iniciar(logar("carlos@example.com"), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoSemToken_quandoInicia_entao401() throws Exception {
        mockMvc.perform(post("/anuncios/{anuncioId}/iniciar", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }
}
