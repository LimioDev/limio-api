package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.CheckoutEnviadoEvent;
import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.ImagensDeTeste;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;
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
 * BDD ponta a ponta (controller -> banco real) do UC "Fazer o check-out com foto" (ETI-90). A escolha do
 * Prestador ainda não tem usecase: candidaturas nascem pelo repository. O storage é mock.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class CheckoutStoryTest {

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
    private ApplicationEvents eventos;

    @MockitoBean
    private ArmazenamentoService armazenamentoService;

    private Usuario maria;
    private Usuario carlos;
    private Usuario joana;

    @BeforeEach
    void prepararBase() {
        limparBase();
        maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        carlos = usuario("Carlos Souza", "carlos@example.com", "11144477735", PapelUsuario.PRESTADOR);
        joana = usuario("Joana Lima", "joana@example.com", "39053344705", PapelUsuario.PRESTADOR);
    }

    @AfterEach
    void limparBase() {
        // Ordem inversa de dependência; as tabelas de outros módulos podem ter sobrado de outras stories.
        jdbc.execute("delete from midia");
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
        jdbc.execute("delete from endereco_salvo");
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from verificacao_contato");
        sessaoRepository.deleteAll();
        jdbc.execute("delete from tentativa_login");
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
                .prazoValidacaoHoras(24)
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

    private Anuncio anuncioComCarlosEscolhido(StatusAnuncio status) {
        Anuncio anuncio = anuncio(status);
        aplicacao(anuncio.getId(), carlos.getId(), StatusAplicacao.ESCOLHIDA);
        return anuncio;
    }

    private static MockMultipartFile foto(String nome, String tipo, byte[] conteudo) {
        return new MockMultipartFile("arquivos", nome, tipo, conteudo);
    }

    private static MockMultipartFile jpeg() {
        return foto("servico.jpg", "image/jpeg", ImagensDeTeste.jpeg(40, 30));
    }

    private ResultActions checkout(String token, UUID anuncioId, String observacao, MockMultipartFile... fotos)
            throws Exception {
        MockMultipartHttpServletRequestBuilder requisicao = multipart("/anuncios/" + anuncioId + "/checkout");
        for (MockMultipartFile foto : fotos) {
            requisicao.file(foto);
        }
        if (observacao != null) {
            requisicao.param("observacao", observacao);
        }
        return mockMvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions checkout(String token, UUID anuncioId, MockMultipartFile... fotos) throws Exception {
        return checkout(token, anuncioId, null, fotos);
    }

    private Anuncio recarregar(UUID anuncioId) {
        return anuncioRepository.findById(anuncioId).orElseThrow();
    }

    private StatusAplicacao statusDaCandidatura(UUID anuncioId, UUID prestadorId) {
        return aplicacaoRepository.findAll().stream()
                .filter(a -> a.getAnuncioId().equals(anuncioId) && a.getPrestadorId().equals(prestadorId))
                .findFirst().orElseThrow().getStatus();
    }

    private int totalEvidencias() {
        return jdbc.queryForObject("select count(*) from midia where tipo = 'EVIDENCIA_CHECKOUT'", Integer.class);
    }

    @Test
    void dadoServicoEmAndamento_quandoPrestadorEscolhidoFazCheckout_entao200AguardandoValidacao() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.EM_ANDAMENTO);

        checkout(logar("carlos@example.com"), anuncio.getId(), "Tudo certo, deixei o local limpo.", jpeg(),
                foto("depois.png", "image/png", ImagensDeTeste.png(20, 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("AGUARDANDO_VALIDACAO")))
                .andExpect(jsonPath("$.checkoutEm", notNullValue()))
                .andExpect(jsonPath("$.prazoValidacaoEm", notNullValue()));

        Anuncio salvo = recarregar(anuncio.getId());
        assertThat(salvo.getStatus()).isEqualTo(StatusAnuncio.AGUARDANDO_VALIDACAO);
        assertThat(salvo.getCheckoutEm()).isNotNull();
        assertThat(salvo.getObservacaoCheckout()).isEqualTo("Tudo certo, deixei o local limpo.");
        assertThat(salvo.getValidadoEm()).isNull();
        assertThat(statusDaCandidatura(anuncio.getId(), carlos.getId())).isEqualTo(StatusAplicacao.AGUARDANDO_VALIDACAO);
        assertThat(jdbc.queryForObject(
                "select count(*) from midia where tipo = 'EVIDENCIA_CHECKOUT' and anuncio_id = ?::uuid "
                        + "and usuario_id = ?::uuid and exif_removido",
                Integer.class, anuncio.getId(), carlos.getId())).isEqualTo(2);
        verify(armazenamentoService, org.mockito.Mockito.times(2))
                .enviar(startsWith("fotos-checkout/" + anuncio.getId() + "/"), any(), anyString());
        assertThat(eventos.stream(CheckoutEnviadoEvent.class)).hasSize(1);
    }

    @Test
    void dadoCheckoutFeito_quandoCalculaPrazo_entaoSoma24HorasAoCheckoutEm() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.EM_ANDAMENTO);

        String corpo = checkout(logar("carlos@example.com"), anuncio.getId(), jpeg())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Instant checkoutEm = Instant.parse(objectMapper.readTree(corpo).get("checkoutEm").asString());
        Instant prazo = Instant.parse(objectMapper.readTree(corpo).get("prazoValidacaoEm").asString());
        assertThat(Duration.between(checkoutEm, prazo)).isEqualTo(Duration.ofHours(24));
        assertThat(recarregar(anuncio.getId()).getCheckoutEm()).isCloseTo(checkoutEm,
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void dadoServicoCombinadoSemTerIniciado_quandoPrestadorEscolhidoFazCheckout_entao200() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.PUBLICADO);

        checkout(logar("carlos@example.com"), anuncio.getId(), jpeg())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("AGUARDANDO_VALIDACAO")));

        assertThat(recarregar(anuncio.getId()).getStatus()).isEqualTo(StatusAnuncio.AGUARDANDO_VALIDACAO);
        assertThat(totalEvidencias()).isEqualTo(1);
    }

    @Test
    void dadoCheckoutSemFoto_quandoEnvia_entao422FotoObrigatoriaENadaMuda() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.EM_ANDAMENTO);

        checkout(logar("carlos@example.com"), anuncio.getId(), "Sem foto")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo", is("FOTO_OBRIGATORIA")));

        assertThat(recarregar(anuncio.getId()).getStatus()).isEqualTo(StatusAnuncio.EM_ANDAMENTO);
        assertThat(recarregar(anuncio.getId()).getCheckoutEm()).isNull();
        assertThat(totalEvidencias()).isZero();
        assertThat(eventos.stream(CheckoutEnviadoEvent.class)).isEmpty();
    }

    @Test
    void dadoPrestadorNaoEscolhido_quandoFazCheckout_entao404ENadaMuda() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.EM_ANDAMENTO);
        aplicacao(anuncio.getId(), joana.getId(), StatusAplicacao.RECUSADA);

        checkout(logar("joana@example.com"), anuncio.getId(), jpeg())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));

        assertThat(recarregar(anuncio.getId()).getStatus()).isEqualTo(StatusAnuncio.EM_ANDAMENTO);
        assertThat(totalEvidencias()).isZero();
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
        assertThat(eventos.stream(CheckoutEnviadoEvent.class)).isEmpty();
    }

    @Test
    void dadoNaoEscolhidoSemFoto_quandoFazCheckout_entao404EmVezDe422() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.EM_ANDAMENTO);

        checkout(logar("joana@example.com"), anuncio.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoEmpregadorDoAnuncio_quandoFazCheckout_entao404() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.EM_ANDAMENTO);

        checkout(logar("maria@example.com"), anuncio.getId(), jpeg())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoAnuncioInexistente_quandoFazCheckout_entao404() throws Exception {
        checkout(logar("carlos@example.com"), UUID.randomUUID(), jpeg())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("SERVICO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoServicoJaAguardandoValidacao_quandoFazCheckout_entao409ENadaMuda() throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.AGUARDANDO_VALIDACAO);
        aplicacao(anuncio.getId(), carlos.getId(), StatusAplicacao.AGUARDANDO_VALIDACAO);

        checkout(logar("carlos@example.com"), anuncio.getId(), jpeg())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("SERVICO_ESTADO_INVALIDO")));

        assertThat(totalEvidencias()).isZero();
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
        assertThat(eventos.stream(CheckoutEnviadoEvent.class)).isEmpty();
    }

    @Test
    void dadoServicoConcluido_quandoFazCheckout_entao409() throws Exception {
        Anuncio anuncio = anuncio(StatusAnuncio.CONCLUIDO);
        aplicacao(anuncio.getId(), carlos.getId(), StatusAplicacao.CONCLUIDA);

        // CONCLUIDA não é mais "escolhida" pro status da candidatura: quem chega aqui só vê 404.
        checkout(logar("carlos@example.com"), anuncio.getId(), jpeg())
                .andExpect(status().isNotFound());
    }

    @Test
    void dadoArquivoQueNaoEImagem_quandoFazCheckout_entao422FotoInvalidaENadaMuda() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.EM_ANDAMENTO);

        checkout(logar("carlos@example.com"), anuncio.getId(), jpeg(),
                foto("a.gif", "image/gif", ImagensDeTeste.gif(10, 10)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo", is("FOTO_INVALIDA")));

        assertThat(recarregar(anuncio.getId()).getStatus()).isEqualTo(StatusAnuncio.EM_ANDAMENTO);
        assertThat(totalEvidencias()).isZero();
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
    }

    @Test
    void dadaFotoMaiorQueOLimite_quandoFazCheckout_entao422FotoInvalida() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.EM_ANDAMENTO);
        byte[] grande = new byte[5 * 1024 * 1024 + 1];

        checkout(logar("carlos@example.com"), anuncio.getId(), foto("grande.jpg", "image/jpeg", grande))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo", is("FOTO_INVALIDA")));

        assertThat(totalEvidencias()).isZero();
    }

    @Test
    void dadoMaisFotosQueOMaximo_quandoFazCheckout_entao422FotoInvalida() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.EM_ANDAMENTO);

        checkout(logar("carlos@example.com"), anuncio.getId(), jpeg(), jpeg(), jpeg(), jpeg(), jpeg(), jpeg())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo", is("FOTO_INVALIDA")));

        assertThat(totalEvidencias()).isZero();
    }

    @Test
    void dadoStorageForaDoAr_quandoFazCheckout_entao503EAnuncioContinuaIgual() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.EM_ANDAMENTO);
        doThrow(new ArmazenamentoIndisponivelException()).when(armazenamentoService)
                .enviar(anyString(), any(), anyString());

        checkout(logar("carlos@example.com"), anuncio.getId(), jpeg())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo", is("ARMAZENAMENTO_INDISPONIVEL")));

        assertThat(recarregar(anuncio.getId()).getStatus()).isEqualTo(StatusAnuncio.EM_ANDAMENTO);
        assertThat(statusDaCandidatura(anuncio.getId(), carlos.getId())).isEqualTo(StatusAplicacao.ESCOLHIDA);
        assertThat(totalEvidencias()).isZero();
        assertThat(eventos.stream(CheckoutEnviadoEvent.class)).isEmpty();
    }

    @Test
    void dadoCliqueDuplo_quandoFazCheckoutDuasVezes_entaoUm200EUm409() throws Exception {
        Anuncio anuncio = anuncioComCarlosEscolhido(StatusAnuncio.EM_ANDAMENTO);
        String token = logar("carlos@example.com");
        Callable<Integer> tarefa = () -> checkout(token, anuncio.getId(), jpeg()).andReturn().getResponse().getStatus();

        List<Integer> status = emParalelo(List.of(tarefa, tarefa));

        assertThat(status).containsExactlyInAnyOrder(200, 409);
        assertThat(totalEvidencias()).isEqualTo(1);
        assertThat(eventos.stream(CheckoutEnviadoEvent.class)).hasSize(1);
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
    void dadoSemToken_quandoFazCheckout_entao401() throws Exception {
        mockMvc.perform(multipart("/anuncios/" + UUID.randomUUID() + "/checkout").file(jpeg()))
                .andExpect(status().isUnauthorized());
    }
}
