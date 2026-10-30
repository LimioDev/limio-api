package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
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
import com.limio.api.comum.eventos.CandidaturaCriadaEvent;
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
 * BDD ponta a ponta (controller -> banco real) do UC "Candidatar-se a um anúncio" (ETI-75). Não existe
 * usecase de "criar anúncio" ainda: anúncios e candidaturas nascem direto pelo repository, igual
 * {@code ConsultarPainelEmpregadorStoryTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class CandidatarSeStoryTest {

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

    private Usuario prestador(String nome, String email, String cpf) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nome)
                .email(email)
                .telefone("11999998888")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(25))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(PapelUsuario.PRESTADOR)
                .statusConta(StatusConta.ATIVA)
                .telefoneConfirmadoEm(Instant.now())
                .emailConfirmadoEm(Instant.now())
                .build());
    }

    private Usuario prestadorSemContatosConfirmados(String nome, String email, String cpf) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nome)
                .email(email)
                .telefone("11999998888")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(25))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(PapelUsuario.PRESTADOR)
                .statusConta(StatusConta.ATIVA)
                .build());
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
                .categoriaId(UUID.randomUUID()) // sem FK pra categoria (ADR-0001 §1.6): qualquer id serve aqui
                .descricao("Instalar 3 tomadas novas na sala e trocar o disjuntor")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(5)))
                .bairroRegiaoSnapshot("Pinheiros")
                .status(status)
                .build());
    }

    private Aplicacao aplicacao(UUID anuncioId, UUID prestadorId, StatusAplicacao status) {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(prestadorId)
                .status(status)
                .dataAplicacao(Instant.now())
                .build();
        if (status == StatusAplicacao.ESCOLHIDA) {
            aplicacao.setEscolhidaEm(Instant.now());
        }
        return aplicacaoRepository.save(aplicacao);
    }

    private ResultActions candidatarSe(String token, UUID anuncioId) throws Exception {
        return mockMvc.perform(post("/anuncios/{anuncioId}/candidaturas", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void dadoPrestadorElegivel_quandoCandidataSe_entao201EEventoPublicado() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "11144477735");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);

        candidatarSe(logar("rafael@example.com"), anuncio.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ENVIADA")));

        List<Aplicacao> salvas = aplicacaoRepository.findAll();
        assertThat(salvas).hasSize(1);
        Aplicacao salva = salvas.get(0);
        assertThat(salva.getAnuncioId()).isEqualTo(anuncio.getId());
        assertThat(salva.getPrestadorId()).isEqualTo(rafael.getId());
        assertThat(salva.getStatus()).isEqualTo(StatusAplicacao.ENVIADA);

        List<CandidaturaCriadaEvent> publicados = eventos.stream(CandidaturaCriadaEvent.class).toList();
        assertThat(publicados).hasSize(1);
        CandidaturaCriadaEvent evento = publicados.get(0);
        assertThat(evento.candidaturaId()).isEqualTo(salva.getId());
        assertThat(evento.anuncioId()).isEqualTo(anuncio.getId());
        assertThat(evento.anuncioTitulo()).isEqualTo("Instalar 3 tomadas");
        assertThat(evento.contratanteId()).isEqualTo(maria.getId());
        assertThat(evento.prestadorId()).isEqualTo(rafael.getId());
    }

    @Test
    void dadoPapelAtivoEmpregador_quandoCandidataSe_entao403AcessoNegado() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Usuario outroEmpregador = empregador("João Pereira", "joao@example.com", "11144477735");
        Anuncio anuncio = anuncio(outroEmpregador.getId(), StatusAnuncio.PUBLICADO);

        candidatarSe(logar("maria@example.com"), anuncio.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
    }

    @Test
    void dadoContatosNaoConfirmados_quandoCandidataSe_entao403ContatosNaoConfirmados() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        prestadorSemContatosConfirmados("Rafael Souza", "rafael@example.com", "11144477735");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);

        candidatarSe(logar("rafael@example.com"), anuncio.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("CONTATOS_NAO_CONFIRMADOS")));
    }

    @Test
    void dadoAnuncioInexistente_quandoCandidataSe_entao404() throws Exception {
        prestador("Rafael Souza", "rafael@example.com", "11144477735");

        candidatarSe(logar("rafael@example.com"), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoAnuncioDoProprioPrestador_quandoCandidataSe_entao403AnuncioProprio() throws Exception {
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "11144477735");
        Anuncio proprio = anuncio(rafael.getId(), StatusAnuncio.PUBLICADO);

        candidatarSe(logar("rafael@example.com"), proprio.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_PROPRIO")));
    }

    @Test
    void dadoAnuncioConcluido_quandoCandidataSe_entao409AnuncioEstadoInvalido() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        prestador("Rafael Souza", "rafael@example.com", "11144477735");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.CONCLUIDO);

        candidatarSe(logar("rafael@example.com"), anuncio.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_ESTADO_INVALIDO")));
    }

    @Test
    void dadoAnuncioPublicadoComCandidaturaEscolhida_quandoCandidataSe_entao409AnuncioEstadoInvalido() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Usuario escolhido = prestador("Pedro Lima", "pedro@example.com", "10433218100");
        prestador("Rafael Souza", "rafael@example.com", "11144477735");
        Anuncio anuncioComEscolhido = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        aplicacao(anuncioComEscolhido.getId(), escolhido.getId(), StatusAplicacao.ESCOLHIDA);

        candidatarSe(logar("rafael@example.com"), anuncioComEscolhido.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_ESTADO_INVALIDO")));
    }

    @Test
    void dadoCandidaturaAtivaDoMesmoPar_quandoCandidataDeNovo_entao409CandidaturaDuplicada() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        prestador("Rafael Souza", "rafael@example.com", "11144477735");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        String token = logar("rafael@example.com");

        candidatarSe(token, anuncio.getId()).andExpect(status().isCreated());
        candidatarSe(token, anuncio.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CANDIDATURA_DUPLICADA")));

        assertThat(aplicacaoRepository.findAll()).hasSize(1);
    }

    @Test
    void dadoCandidaturaAnteriorRecusadaNoMesmoAnuncio_quandoCandidataDeNovo_entao201() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "11144477735");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        aplicacao(anuncio.getId(), rafael.getId(), StatusAplicacao.RECUSADA);

        candidatarSe(logar("rafael@example.com"), anuncio.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ENVIADA")));

        assertThat(aplicacaoRepository.findAll()).hasSize(2);
    }

    @Test
    void dadoPrestadorFreeComQuatroEscolhasNoMes_quandoCandidataSe_entao403CotaEsgotada() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Usuario rafael = prestador("Rafael Souza", "rafael@example.com", "11144477735");
        for (int i = 0; i < 4; i++) {
            Anuncio concluido = anuncio(maria.getId(), StatusAnuncio.CONCLUIDO);
            aplicacao(concluido.getId(), rafael.getId(), StatusAplicacao.ESCOLHIDA);
        }
        Anuncio novoAnuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);

        candidatarSe(logar("rafael@example.com"), novoAnuncio.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("COTA_ESGOTADA")));
    }

    @Test
    void dadoSemToken_quandoCandidataSe_entao401() throws Exception {
        mockMvc.perform(post("/anuncios/{anuncioId}/candidaturas", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }

    /**
     * Clique duplo: duas requisições simultâneas pro mesmo par anúncio+prestador. A checagem do usecase
     * sozinha não garante nada sob corrida real — só o índice único {@code uk_aplicacao_par_ativo} evita
     * as duas candidaturas (ETI-75).
     */
    @Test
    void dadoDuasCandidaturasSimultaneasDoMesmoPar_entaoSoUmaCria() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        prestador("Rafael Souza", "rafael@example.com", "11144477735");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        String token = logar("rafael@example.com");

        List<Callable<Integer>> tarefas = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tarefas.add(() -> candidatarSe(token, anuncio.getId()).andReturn().getResponse().getStatus());
        }
        List<Integer> status = emParalelo(tarefas);

        assertThat(status.stream().filter(s -> s == 201).toList()).hasSize(1);
        assertThat(status.stream().filter(s -> s == 409).toList()).hasSize(1);
        assertThat(aplicacaoRepository.findAll()).hasSize(1);
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
}
