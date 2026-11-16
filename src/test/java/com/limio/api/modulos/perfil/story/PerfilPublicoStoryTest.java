package com.limio.api.modulos.perfil.story;

import static org.hamcrest.Matchers.is;
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
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.moderacao.avaliacao.Avaliacao;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoRepository;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do UC "Consultar perfil público" (ETI-105). Quantidade/média
 * de avaliações vêm das colunas de {@code perfil_empregador}/{@code perfil_prestador} (ETI-107) — gravadas
 * aqui direto pelo repository, sem publicar {@code AvaliacaoPublicadaEvent}: o listener que faz essa escrita
 * ({@code RegistrarMediaAvaliacoesUseCase}) já tem story test próprio em {@code moderacao}; aqui o que importa
 * é o perfil público ler a coluna certa. Avaliações recentes, por outro lado, vêm de {@code Avaliacao} de
 * verdade (semeada pelo repository) — essa parte passa pela implementação real de {@code
 * ConsultaAvaliacoesService} (`modulos.moderacao`), sem mock. Anúncio/candidatura concluídos nascem direto
 * pelo repository, como em {@code anuncio.story.ConsultarServicoStoryTest}: não existe usecase de "concluir
 * serviço" ainda e {@code anuncio_id}/{@code prestador_id} não têm FK (ADR-0001 §1.6), então um id solto é
 * suficiente.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PerfilPublicoStoryTest {

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
    private PerfilEmpregadorRepository perfilEmpregadorRepository;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    @Autowired
    private AvaliacaoRepository avaliacaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void limparBase() {
        avaliacaoRepository.deleteAll();
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from verificacao_contato");
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

    private ResultActions consultar(String token, UUID usuarioId, String papel) throws Exception {
        return mockMvc.perform(get("/perfis-publicos/{id}", usuarioId)
                .param("papel", papel)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Anuncio anuncioConcluidoDe(UUID contratanteId) {
        UUID categoriaId = jdbc.queryForObject("select id from categoria where protegida", UUID.class);
        return anuncioRepository.save(Anuncio.builder()
                .titulo("Instalar 3 tomadas")
                .categoriaId(categoriaId)
                .contratanteId(contratanteId)
                .descricao("Trocar três tomadas da cozinha")
                .dataHoraPretendida(Instant.now().minus(Duration.ofDays(1)))
                .bairroRegiaoSnapshot("Pinheiros")
                .enderecoCompletoSnapshot("Rua dos Pinheiros, 100, Pinheiros, São Paulo - SP")
                .status(StatusAnuncio.CONCLUIDO)
                .build());
    }

    /**
     * Nenhum fluxo marca {@code Aplicacao} como {@code CONCLUIDA} (ver {@code AplicacaoRepository}): o
     * histórico conta pelo {@code Anuncio} concluído com {@code escolhidaEm} preenchido, então o seed
     * cria os dois, não força o status direto.
     */
    private Aplicacao aplicacaoConcluidaDe(UUID prestadorId) {
        Anuncio anuncio = anuncioConcluidoDe(UUID.randomUUID());
        return aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(prestadorId)
                .status(StatusAplicacao.ESCOLHIDA)
                .dataAplicacao(Instant.now().minus(Duration.ofDays(10)))
                .escolhidaEm(Instant.now().minus(Duration.ofDays(9)))
                .build());
    }

    @Test
    void dadoEmpregadorComAvaliacoesEFotoESemAbrirOProprioPerfilAntes_quandoConsultaOutroUsuario_entao200() throws Exception {
        Usuario maria = cadastrar("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario joao = cadastrar("João Pereira", "joao@example.com", "15350946056", PapelUsuario.PRESTADOR);
        perfilEmpregadorRepository.save(PerfilEmpregador.builder().usuarioId(maria.getId()).nomeExibicao("Mari")
                .mediaAvaliacoes(new java.math.BigDecimal("4.5")).qtdAvaliacoes(8).build());
        anuncioConcluidoDe(maria.getId());
        String tokenJoao = logar("joao@example.com");

        consultar(tokenJoao, maria.getId(), "EMPREGADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("Mari")))
                .andExpect(jsonPath("$.mediaAvaliacoes", is(4.5)))
                .andExpect(jsonPath("$.qtdAvaliacoes", is(8)))
                .andExpect(jsonPath("$.servicosConcluidos", is(1)))
                .andExpect(jsonPath("$.especialidades").isEmpty())
                .andExpect(jsonPath("$.avaliacoesRecentes").isEmpty());
    }

    @Test
    void dadoEmpregadorQueNuncaAbriuOPerfil_quandoConsulta_entaoNomeInicialESemAvaliacoes() throws Exception {
        Usuario maria = cadastrar("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        String token = logar("maria@example.com");

        consultar(token, maria.getId(), "EMPREGADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("Maria da Silva")))
                .andExpect(jsonPath("$.fotoUrl", nullValue()))
                .andExpect(jsonPath("$.mediaAvaliacoes", nullValue()))
                .andExpect(jsonPath("$.qtdAvaliacoes", is(0)))
                .andExpect(jsonPath("$.servicosConcluidos", is(0)));

        // próprio perfil: não cria o perfil de empregador nem exige abrir /conta/perfil-empregador antes.
        assertZeroPerfis();
    }

    private void assertZeroPerfis() {
        Integer total = jdbc.queryForObject("select count(*) from perfil_empregador", Integer.class);
        org.assertj.core.api.Assertions.assertThat(total).isZero();
    }

    @Test
    void dadoPrestadorComServicosConcluidos_quandoConsulta_entaoNomeAbreviadoEHistorico() throws Exception {
        Usuario rafael = cadastrar("Rafael da Silva Souza", "rafael@example.com", "15350946056", PapelUsuario.PRESTADOR);
        Usuario maria = cadastrar("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        aplicacaoConcluidaDe(rafael.getId());
        aplicacaoConcluidaDe(rafael.getId());
        String tokenMaria = logar("maria@example.com");

        consultar(tokenMaria, rafael.getId(), "PRESTADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("Rafael S.")))
                .andExpect(jsonPath("$.mediaAvaliacoes", nullValue()))
                .andExpect(jsonPath("$.qtdAvaliacoes", is(0)))
                .andExpect(jsonPath("$.servicosConcluidos", is(2)))
                .andExpect(jsonPath("$.especialidades").isEmpty());
    }

    @Test
    void dadoContaEncerrada_quandoConsulta_entao200ComNomeContaEncerradaSemFotoMasComHistorico() throws Exception {
        Usuario maria = cadastrar("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario joao = cadastrar("João Pereira", "joao@example.com", "15350946056", PapelUsuario.PRESTADOR);
        String tokenMaria = logar("maria@example.com");
        mockMvc.perform(get("/conta/perfil-empregador").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenMaria))
                .andExpect(status().isOk());
        anuncioConcluidoDe(maria.getId());
        mockMvc.perform(post("/auth/encerrar-conta")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenMaria)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("confirmacao", true))))
                .andExpect(status().isOk());
        String tokenJoao = logar("joao@example.com");

        consultar(tokenJoao, maria.getId(), "EMPREGADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("Conta encerrada")))
                .andExpect(jsonPath("$.fotoUrl", nullValue()))
                .andExpect(jsonPath("$.servicosConcluidos", is(1)));
    }

    @Test
    void dadoUsuarioInexistente_quandoConsulta_entao404() throws Exception {
        Usuario joao = cadastrar("João Pereira", "joao@example.com", "15350946056", PapelUsuario.PRESTADOR);
        String token = logar("joao@example.com");

        consultar(token, UUID.randomUUID(), "EMPREGADOR")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("PERFIL_NAO_ENCONTRADO")));
    }

    @Test
    void dadoPapelInvalido_quandoConsulta_entao400() throws Exception {
        Usuario maria = cadastrar("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        String token = logar("maria@example.com");

        consultar(token, maria.getId(), "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoSemToken_quandoConsulta_entao401() throws Exception {
        mockMvc.perform(get("/perfis-publicos/{id}", UUID.randomUUID()).param("papel", "EMPREGADOR"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }

    @Test
    void dadoUsuarioLogado_quandoConsultaOProprioPerfil_entao200() throws Exception {
        Usuario maria = cadastrar("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        String token = logar("maria@example.com");

        consultar(token, maria.getId(), "EMPREGADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("Maria da Silva")));
    }

    @Test
    void dadoAvaliacoesReaisRecebidas_quandoConsulta_entaoAvaliacoesRecentesComNomeAbreviado() throws Exception {
        Usuario rafael = cadastrar("Rafael da Silva Souza", "rafael@example.com", "15350946056", PapelUsuario.PRESTADOR);
        Usuario maria = cadastrar("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        avaliacaoRepository.save(Avaliacao.publicar(UUID.randomUUID(), maria.getId(), rafael.getId(),
                PapelUsuario.PRESTADOR, 5, "Pontual e educado.", Instant.now().minus(Duration.ofDays(1))));
        String tokenMaria = logar("maria@example.com");

        consultar(tokenMaria, rafael.getId(), "PRESTADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avaliacoesRecentes[0].avaliadorNome", is("Maria S.")))
                .andExpect(jsonPath("$.avaliacoesRecentes[0].nota", is(5)))
                .andExpect(jsonPath("$.avaliacoesRecentes[0].comentario", is("Pontual e educado.")));
    }
}
