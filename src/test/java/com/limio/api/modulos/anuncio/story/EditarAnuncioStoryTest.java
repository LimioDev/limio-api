package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.transaction.support.TransactionTemplate;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
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
 * BDD ponta a ponta (controller -> banco real) do UC19 "Editar anúncio antes da escolha" (ETI-61). Como em
 * {@code CancelarAnuncioStoryTest}, anúncios e candidaturas nascem direto pelo repository. Não existe usecase
 * de escolher candidato: a escolha concorrente é simulada gravando a candidatura {@code ESCOLHIDA} sob a mesma
 * trava que o usecase de escolha vai tomar.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EditarAnuncioStoryTest {

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
    private TransactionTemplate transacao;

    @Autowired
    private TravaTransacional trava;

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

    private Map<String, Object> corpoValido() {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("categoriaId", categoriaAtiva().toString());
        campos.put("titulo", "Instalar 5 tomadas");
        campos.put("descricao", "Agora são 5 tomadas, na sala e no quarto");
        // Postgres guarda microssegundos: a data do teste já sai truncada pra comparar com o banco.
        campos.put("dataHoraPretendida", Instant.now().plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.MICROS).toString());
        campos.put("numeroPessoas", 2);
        campos.put("observacoes", "Levar escada");
        return campos;
    }

    private ResultActions editar(String token, UUID anuncioId, Map<String, Object> campos) throws Exception {
        return mockMvc.perform(put("/anuncios/{id}", anuncioId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(campos)));
    }

    private void assertSemMudanca(UUID anuncioId) {
        assertThat(anuncioRepository.findById(anuncioId).orElseThrow().getTitulo()).isEqualTo("Instalar 3 tomadas");
    }

    @Test
    void dadoAnuncioPublicadoSemEscolhida_quandoEdita_entao200ComAnuncioAtualizado() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        aplicacao(anuncio.getId(), StatusAplicacao.ENVIADA);
        String token = logar("maria@example.com");
        Map<String, Object> campos = corpoValido();

        editar(token, anuncio.getId(), campos)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(anuncio.getId().toString())))
                .andExpect(jsonPath("$.status", is("PUBLICADO")))
                .andExpect(jsonPath("$.titulo", is("Instalar 5 tomadas")))
                .andExpect(jsonPath("$.descricao", is("Agora são 5 tomadas, na sala e no quarto")))
                .andExpect(jsonPath("$.numeroPessoas", is(2)))
                .andExpect(jsonPath("$.observacoes", is("Levar escada")))
                .andExpect(jsonPath("$.candidatos", is(1)));

        Anuncio editado = anuncioRepository.findById(anuncio.getId()).orElseThrow();
        assertThat(editado.getTitulo()).isEqualTo("Instalar 5 tomadas");
        assertThat(editado.getDataHoraPretendida())
                .isEqualTo(Instant.parse((String) campos.get("dataHoraPretendida")));
        assertThat(editado.getTamanhoLocal()).isNull();
        assertThat(editado.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(editado.getEnderecoCompletoSnapshot()).isEqualTo("Rua dos Pinheiros, 100, Pinheiros, São Paulo - SP");

        mockMvc.perform(get("/anuncios/{id}", anuncio.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo", is("Instalar 5 tomadas")));
    }

    @Test
    void dadoAnuncioPublicadoComCandidaturaEscolhida_quandoEdita_entao409() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        aplicacao(anuncio.getId(), StatusAplicacao.ESCOLHIDA);

        editar(logar("maria@example.com"), anuncio.getId(), corpoValido())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_ESTADO_INVALIDO")));

        assertSemMudanca(anuncio.getId());
    }

    @Test
    void dadoAnuncioEncerrado_quandoEdita_entao409() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.ENCERRADO);

        editar(logar("maria@example.com"), anuncio.getId(), corpoValido())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_ESTADO_INVALIDO")));

        assertSemMudanca(anuncio.getId());
    }

    @Test
    void dadoDataNoPassado_quandoEdita_entao422() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Map<String, Object> campos = corpoValido();
        campos.put("dataHoraPretendida", Instant.now().minus(Duration.ofDays(1)).toString());

        editar(logar("maria@example.com"), anuncio.getId(), campos)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo", is("DATA_NO_PASSADO")));

        assertSemMudanca(anuncio.getId());
    }

    @Test
    void dadoCategoriaInativa_quandoEdita_entao422() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        UUID inativa = jdbc.queryForObject(
                "select id from categoria where not protegida and ativa limit 1", UUID.class);
        jdbc.update("update categoria set ativa = false where id = ?", inativa);
        try {
            Map<String, Object> campos = corpoValido();
            campos.put("categoriaId", inativa.toString());

            editar(logar("maria@example.com"), anuncio.getId(), campos)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.codigo", is("CATEGORIA_INVALIDA")));
        } finally {
            jdbc.update("update categoria set ativa = true where id = ?", inativa);
        }

        assertSemMudanca(anuncio.getId());
    }

    @Test
    void dadoTituloCurto_quandoEdita_entao400() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        Map<String, Object> campos = corpoValido();
        campos.put("titulo", "abc");

        editar(logar("maria@example.com"), anuncio.getId(), campos)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        assertSemMudanca(anuncio.getId());
    }

    @Test
    void dadoAnuncioDeOutroEmpregador_quandoEdita_entao404() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Usuario joao = empregador("João Pereira", "joao@example.com", "11144477735");
        Anuncio anuncioDeJoao = anuncio(joao.getId(), StatusAnuncio.PUBLICADO);

        editar(logar("maria@example.com"), anuncioDeJoao.getId(), corpoValido())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_NAO_ENCONTRADO")));

        assertSemMudanca(anuncioDeJoao.getId());
        assertThat(maria.getId()).isNotEqualTo(joao.getId());
    }

    @Test
    void dadoAnuncioInexistente_quandoEdita_entao404() throws Exception {
        empregador("Maria da Silva", "maria@example.com", "52998224725");

        editar(logar("maria@example.com"), UUID.randomUUID(), corpoValido())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoSemToken_quandoEdita_entao401() throws Exception {
        mockMvc.perform(put("/anuncios/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoValido())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }

    /**
     * A "escolha" segura a trava do anúncio, grava a candidatura {@code ESCOLHIDA} e só então confirma. A edição
     * chega no meio, espera a trava e, quando entra, já enxerga a escolha: recusa e não sobrescreve nada.
     */
    @Test
    void dadoEscolhaEmAndamento_quandoEditaAoMesmoTempo_entaoEdicaoRecusadaPorEstadoInvalido() throws Exception {
        Usuario maria = empregador("Maria da Silva", "maria@example.com", "52998224725");
        Anuncio anuncio = anuncio(maria.getId(), StatusAnuncio.PUBLICADO);
        String token = logar("maria@example.com");
        CountDownLatch escolhaComTrava = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> escolha = executor.submit(() -> transacao.executeWithoutResult(tx -> {
                trava.travar(EscopoTrava.ANUNCIO, anuncio.getId().toString());
                escolhaComTrava.countDown();
                pausa(Duration.ofMillis(700));
                aplicacao(anuncio.getId(), StatusAplicacao.ESCOLHIDA);
            }));
            assertThat(escolhaComTrava.await(10, TimeUnit.SECONDS)).isTrue();

            editar(token, anuncio.getId(), corpoValido())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo", is("ANUNCIO_ESTADO_INVALIDO")));

            escolha.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertSemMudanca(anuncio.getId());
        assertThat(aplicacaoRepository.existsByAnuncioIdAndStatusIn(anuncio.getId(),
                List.of(StatusAplicacao.ESCOLHIDA))).isTrue();
    }

    private static void pausa(Duration duracao) {
        try {
            Thread.sleep(duracao);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
