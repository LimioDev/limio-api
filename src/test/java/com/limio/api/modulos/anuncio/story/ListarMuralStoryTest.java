package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaRepository;
import com.limio.api.comum.seguranca.JwtAuthFilter;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.categoria.CategoriaRepository;
import com.limio.api.modulos.perfil.geo.Coordenadas;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorRepository;

/**
 * BDD ponta a ponta (controller -> banco real) do mural do Prestador (ETI-79, US19/US20). Usuários e
 * perfis nascem pelos repositories; o token é emitido com o {@link JwtEncoder} (mesmo formato lido pelo
 * {@link JwtAuthFilter}). Categorias vêm da carga inicial da migration; o storage não é tocado (sem foto,
 * ou foto cujo link o storage de teste gera localmente).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ListarMuralStoryTest {

    private static final String ROTA = "/anuncios/mural";
    private static final Coordenadas CAMPINAS = new Coordenadas(-22.9056, -47.0608);
    /** 0,01° de latitude ≈ 1,11 km. */
    private static final double GRAU_POR_KM = 0.01 / 1.11;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private AnuncioRepository anuncioRepository;
    @Autowired
    private AplicacaoRepository aplicacaoRepository;
    @Autowired
    private MidiaRepository midiaRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private PerfilPrestadorRepository perfilPrestadorRepository;

    private Usuario empregador;

    @BeforeEach
    void limparBase() {
        aplicacaoRepository.deleteAll();
        midiaRepository.deleteAll();
        anuncioRepository.deleteAll();
        perfilPrestadorRepository.deleteAll();
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from sessao");
        jdbc.execute("delete from tentativa_login");
        usuarioRepository.deleteAll();
        empregador = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR, true, true);
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private Usuario usuario(String nome, String email, String cpf, PapelUsuario papel, boolean emailOk, boolean telefoneOk) {
        Instant agora = Instant.now();
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nome)
                .email(email)
                .telefone("11999998888")
                .senhaHash(passwordEncoder.encode("senhaForte123"))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(25))
                .cidadeUf("Campinas/SP")
                .papelAtivo(papel)
                .statusConta(StatusConta.ATIVA)
                .emailConfirmadoEm(emailOk ? agora : null)
                .telefoneConfirmadoEm(telefoneOk ? agora : null)
                .build());
    }

    private Usuario prestador() {
        return usuario("Rafael Souza", "rafael@example.com", "11144477735", PapelUsuario.PRESTADOR, true, true);
    }

    private Categoria categoria(String nome) {
        return categoriaRepository.findAll().stream().filter(c -> c.getNome().equals(nome)).findFirst().orElseThrow();
    }

    private void perfil(Usuario dono, int raioKm, String... especialidades) {
        Set<Categoria> categorias = new HashSet<>();
        for (String nome : especialidades) {
            categorias.add(categoria(nome));
        }
        PerfilPrestador perfil = PerfilPrestador.novo(dono.getId());
        perfil.atualizarAtuacao(categorias, raioKm, "Campinas", Uf.SP, CAMPINAS, List.of());
        perfilPrestadorRepository.save(perfil);
    }

    private Anuncio anuncio(String titulo, String categoria, double kmAoNorte) {
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(empregador.getId())
                .titulo(titulo)
                .categoriaId(categoria(categoria).getId())
                .descricao("Descrição de " + titulo)
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(3)))
                .bairroRegiaoSnapshot("Cambuí")
                .ufSnapshot(Uf.SP)
                .enderecoCompletoSnapshot("Rua Secreta, 100 - Cambuí, Campinas - SP")
                .latitudeSnapshot(CAMPINAS.latitude() + kmAoNorte * GRAU_POR_KM)
                .longitudeSnapshot(CAMPINAS.longitude())
                .status(StatusAnuncio.PUBLICADO)
                .build());
    }

    private String token(Usuario usuario) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.getId().toString())
                .claim(JwtAuthFilter.CLAIM_PAPEL, usuario.getPapelAtivo().name())
                .issuedAt(agora)
                .expiresAt(agora.plus(5, ChronoUnit.MINUTES))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private ResultActions listar(Usuario usuario, String query) throws Exception {
        return mockMvc.perform(get(ROTA + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(usuario)));
    }

    @Test
    void dadoPrestadorHabilitado_quandoListaMural_entaoSoCompativeisPorEspecialidadeOutrosERaioOrdenadosPorDistancia() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica");
        anuncio("longe-8km", "Elétrica", 8);
        anuncio("perto-2km", "Elétrica", 2);
        anuncio("outros-4km", "Outros", 4);
        anuncio("pintura-1km", "Pintura", 1);
        anuncio("eletrica-fora-20km", "Elétrica", 20);

        listar(rafael, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos", is(3)))
                .andExpect(jsonPath("$.content[*].titulo", contains("perto-2km", "outros-4km", "longe-8km")));
    }

    @Test
    void dadoPrestadorComOutros_quandoListaMural_entaoVeTodasAsCategoriasDentroDoRaio() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Outros");
        anuncio("pintura", "Pintura", 1);
        anuncio("eletrica", "Elétrica", 2);

        listar(rafael, "").andExpect(jsonPath("$.content[*].titulo", contains("pintura", "eletrica")));
    }

    @Test
    void dadoAnuncioProprioEComEscolhido_quandoListaMural_entaoNaoAparecem() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica");
        Anuncio escolhido = anuncio("escolhido", "Elétrica", 1);
        anuncio("aberto", "Elétrica", 2);
        aplicacaoRepository.save(Aplicacao.builder().anuncioId(escolhido.getId()).prestadorId(UUID.randomUUID())
                .status(StatusAplicacao.ESCOLHIDA).dataAplicacao(Instant.now()).build());
        anuncioRepository.save(Anuncio.builder().contratanteId(rafael.getId()).titulo("meu").categoriaId(categoria("Elétrica").getId())
                .descricao("meu").dataHoraPretendida(Instant.now().plus(Duration.ofDays(2))).bairroRegiaoSnapshot("Cambuí")
                .latitudeSnapshot(CAMPINAS.latitude()).longitudeSnapshot(CAMPINAS.longitude()).status(StatusAnuncio.PUBLICADO).build());

        listar(rafael, "").andExpect(jsonPath("$.content[*].titulo", contains("aberto")));
    }

    @Test
    void dadoFiltros_quandoListaMural_entaoSoReduzemOConjuntoCompativel() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica", "Pintura");
        anuncio("eletrica-2km", "Elétrica", 2);
        anuncio("pintura-3km", "Pintura", 3);
        anuncio("pintura-9km", "Pintura", 9);
        anuncio("hidraulica", "Hidráulica", 1);

        listar(rafael, "?categorias=" + categoria("Pintura").getId())
                .andExpect(jsonPath("$.content[*].titulo", contains("pintura-3km", "pintura-9km")));
        listar(rafael, "?raio=5").andExpect(jsonPath("$.content[*].titulo", contains("eletrica-2km", "pintura-3km")));
        // filtro de categoria fora da compatibilidade não traz nada (não "amplia")
        listar(rafael, "?categorias=" + categoria("Hidráulica").getId()).andExpect(jsonPath("$.content", hasSize(0)));
        // raio maior que o do Prestador não amplia
        listar(rafael, "?raio=40").andExpect(jsonPath("$.totalElementos", is(3)));
    }

    @Test
    void dadoQuando_quandoListaMural_entaoRecortaPorDataSemOlharJanelasDeAtuacao() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica"); // perfil sem nenhuma janela de atuação
        anuncio("proximo", "Elétrica", 1);
        Anuncio distante = anuncio("distante", "Elétrica", 2);
        jdbc.update("update anuncio set data_hora_pretendida = now() + interval '60 days' where id = ?", distante.getId());

        listar(rafael, "?quando=qualquer").andExpect(jsonPath("$.totalElementos", is(2)));
        listar(rafael, "?quando=esta-semana")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(distante.getId().toString()))));
        listar(rafael, "?quando=fim-de-semana").andExpect(status().isOk());
        listar(rafael, "?quando=mes-que-vem").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoVariosAnuncios_quandoPagina_entaoTotalEDoFiltroInteiro() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 20, "Elétrica");
        for (int i = 1; i <= 5; i++) {
            anuncio("a" + i, "Elétrica", i);
        }

        listar(rafael, "?pagina=1&tamanho=2")
                .andExpect(jsonPath("$.content[*].titulo", contains("a3", "a4")))
                .andExpect(jsonPath("$.pagina", is(1)))
                .andExpect(jsonPath("$.tamanho", is(2)))
                .andExpect(jsonPath("$.totalElementos", is(5)))
                .andExpect(jsonPath("$.totalPaginas", is(3)));
    }

    @Test
    void dadoParametrosInvalidos_quandoListaMural_entao400() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica");

        listar(rafael, "?raio=0").andExpect(status().isBadRequest());
        listar(rafael, "?raio=41").andExpect(status().isBadRequest());
        listar(rafael, "?pagina=-1").andExpect(status().isBadRequest());
        listar(rafael, "?tamanho=51").andExpect(status().isBadRequest());
    }

    @Test
    void dadoItemDoMural_quandoListaMural_entaoTrazOsCamposDaTelaESemDadoDeContatoNemEndereco() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica");
        Anuncio anuncio = anuncio("Trocar chuveiro", "Elétrica", 3);
        midiaRepository.save(Midia.fotoAnuncio(empregador.getId(), anuncio.getId(), "fotos-anuncio/primeira", "image/jpeg", 10, 10, 100));

        String json = listar(rafael, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(anuncio.getId().toString())))
                .andExpect(jsonPath("$.content[0].titulo", is("Trocar chuveiro")))
                .andExpect(jsonPath("$.content[0].categoria.nome", is("Elétrica")))
                .andExpect(jsonPath("$.content[0].empregador.nomeExibicao", is("Maria da Silva")))
                .andExpect(jsonPath("$.content[0].bairroRegiao", is("Cambuí")))
                .andExpect(jsonPath("$.content[0].distanciaKm", is(3.0)))
                .andExpect(jsonPath("$.content[0].dataHoraPretendida").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(json)
                .doesNotContain("Rua Secreta", "enderecoCompleto", "logradouro", "telefone", "email", "whatsapp",
                        "latitude", "longitude", "11999998888", "maria@example.com");
    }

    @Test
    void dadoAnuncioSemFoto_quandoListaMural_entaoFotoNula() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica");
        anuncio("sem-foto", "Elétrica", 1);

        listar(rafael, "").andExpect(jsonPath("$.content[0].fotoUrl", nullValue()));
    }

    @Test
    void dadoEspecialidadesEPerfilIgual_quandoDoisPrestadoresListam_entaoRecebemOMesmoConteudo() throws Exception {
        Usuario rafael = prestador();
        Usuario joana = usuario("Joana Lima", "joana@example.com", "10433218100", PapelUsuario.PRESTADOR, true, true);
        perfil(rafael, 10, "Elétrica");
        perfil(joana, 10, "Elétrica");
        anuncio("igual", "Elétrica", 2);

        String paraRafael = listar(rafael, "").andReturn().getResponse().getContentAsString();
        String paraJoana = listar(joana, "").andReturn().getResponse().getContentAsString();

        assertThat(paraRafael).isEqualTo(paraJoana);
    }

    @Test
    void dadoPapelEmpregador_quandoListaMural_entao403AcessoNegado() throws Exception {
        listar(empregador, "").andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
    }

    @Test
    void dadoEmailOuTelefoneNaoConfirmado_quandoListaMural_entao403ContatosNaoConfirmados() throws Exception {
        Usuario semEmail = usuario("Sem Email", "sememail@example.com", "39053344705", PapelUsuario.PRESTADOR, false, true);
        Usuario semTelefone = usuario("Sem Fone", "semfone@example.com", "12345678909", PapelUsuario.PRESTADOR, true, false);
        perfil(semEmail, 10, "Elétrica");
        perfil(semTelefone, 10, "Elétrica");

        listar(semEmail, "").andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo", is("CONTATOS_NAO_CONFIRMADOS")));
        listar(semTelefone, "").andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo", is("CONTATOS_NAO_CONFIRMADOS")));
    }

    @Test
    void dadoPerfilIncompletoOuInexistente_quandoListaMural_entao403PerfilIncompleto() throws Exception {
        Usuario semPerfil = prestador();
        Usuario semEspecialidade = usuario("Sem Esp", "semesp@example.com", "39053344705", PapelUsuario.PRESTADOR, true, true);
        perfil(semEspecialidade, 10); // nenhuma especialidade => completo = false

        listar(semPerfil, "").andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo", is("PERFIL_INCOMPLETO")));
        listar(semEspecialidade, "").andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo", is("PERFIL_INCOMPLETO")));
    }

    @Test
    void dadoSemToken_quandoListaMural_entao401() throws Exception {
        mockMvc.perform(get(ROTA)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }
}
