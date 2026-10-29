package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
 * BDD ponta a ponta (controller -> banco real) do detalhe no mural do Prestador (ETI-79, US19): detalhe do anúncio. Usuários e
 * perfis nascem pelos repositories; o token é emitido com o {@link JwtEncoder} (mesmo formato lido pelo
 * {@link JwtAuthFilter}). Categorias vêm da carga inicial da migration; o storage não é tocado (sem foto,
 * ou foto cujo link o storage de teste gera localmente).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConsultarDetalheDoMuralStoryTest {

    private static final String ROTA = "/anuncios/mural/";
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
                .cidadeSnapshot("Campinas")
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

    private ResultActions detalhar(Usuario usuario, Object id) throws Exception {
        return mockMvc.perform(get(ROTA + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(usuario)));
    }

    private void candidatura(Anuncio anuncio, Usuario dono, StatusAplicacao status) {
        aplicacaoRepository.save(Aplicacao.builder().anuncioId(anuncio.getId()).prestadorId(dono.getId())
                .status(status).dataAplicacao(Instant.now()).build());
    }

    @Test
    void dadoAnuncioComFotosEReputacao_quandoDetalha_entaoTrazTodosOsDadosDaTelaSemContatoNemEndereco() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica");
        Anuncio anuncio = anuncio("Trocar chuveiro", "Elétrica", 3);
        for (String chave : List.of("a", "b", "c")) {
            midiaRepository.saveAndFlush(Midia.fotoAnuncio(empregador.getId(), anuncio.getId(), "fotos-anuncio/" + chave,
                    "image/jpeg", 10, 10, 100));
        }
        jdbc.update("insert into perfil_empregador (usuario_id, nome_exibicao, media_avaliacoes, qtd_avaliacoes) values (?, 'Mari S.', 4.5, 8)",
                empregador.getId());

        String json = detalhar(rafael, anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(anuncio.getId().toString())))
                .andExpect(jsonPath("$.titulo", is("Trocar chuveiro")))
                .andExpect(jsonPath("$.descricao", is("Descrição de Trocar chuveiro")))
                .andExpect(jsonPath("$.categoria.nome", is("Elétrica")))
                .andExpect(jsonPath("$.fotos", hasSize(3)))
                .andExpect(jsonPath("$.bairroRegiao", is("Cambuí")))
                .andExpect(jsonPath("$.cidade", is("Campinas")))
                .andExpect(jsonPath("$.uf", is("SP")))
                .andExpect(jsonPath("$.distanciaKm", is(3.0)))
                .andExpect(jsonPath("$.empregador.nomeExibicao", is("Mari S.")))
                .andExpect(jsonPath("$.empregador.mediaAvaliacoes", is(4.5)))
                .andExpect(jsonPath("$.empregador.qtdAvaliacoes", is(8)))
                .andExpect(jsonPath("$.minhaCandidatura", nullValue()))
                .andReturn().getResponse().getContentAsString();

        assertThat(json).doesNotContain("Rua Secreta", "enderecoCompleto", "logradouro", "telefone", "email",
                "whatsapp", "latitude", "longitude", "11999998888", "maria@example.com");
    }

    @Test
    void dadoCandidaturaDoPrestador_quandoDetalha_entaoTrazASituacaoDela() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica");
        Anuncio anuncio = anuncio("com-candidatura", "Elétrica", 1);
        candidatura(anuncio, rafael, StatusAplicacao.ENVIADA);

        detalhar(rafael, anuncio.getId()).andExpect(jsonPath("$.minhaCandidatura", is("ENVIADA")));
    }

    @Test
    void dadoCandidaturaSoDeOutroPrestador_quandoDetalha_entaoMinhaCandidaturaNula() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica");
        Anuncio anuncio = anuncio("de-outro", "Elétrica", 1);
        candidatura(anuncio, usuario("Joana Lima", "joana@example.com", "10433218100", PapelUsuario.PRESTADOR, true, true),
                StatusAplicacao.ENVIADA);

        detalhar(rafael, anuncio.getId()).andExpect(jsonPath("$.minhaCandidatura", nullValue()));
    }

    @Test
    void dadoAnuncioForaDoRaioEDeOutraCategoria_quandoDetalha_entaoAindaAbre() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 5, "Elétrica");
        Anuncio anuncio = anuncio("fora-de-tudo", "Pintura", 30);

        detalhar(rafael, anuncio.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanciaKm", closeTo(30.0, 0.2))); // 5 km de raio: passou de longe
    }

    @Test
    void dadoAnuncioInexistente_quandoDetalha_entao404() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica");

        detalhar(rafael, UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoIdQueNaoEUuid_quandoDetalha_entao400() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica");

        detalhar(rafael, "abc").andExpect(status().isBadRequest());
    }

    @Test
    void dadoAnuncioProprioOuEncerradoOuComEscolhido_quandoDetalha_entao404() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica");
        Anuncio proprio = anuncioRepository.save(Anuncio.builder().contratanteId(rafael.getId()).titulo("meu")
                .categoriaId(categoria("Elétrica").getId()).descricao("meu").dataHoraPretendida(Instant.now().plus(Duration.ofDays(2)))
                .bairroRegiaoSnapshot("Cambuí").latitudeSnapshot(CAMPINAS.latitude()).longitudeSnapshot(CAMPINAS.longitude())
                .status(StatusAnuncio.PUBLICADO).build());
        Anuncio encerrado = anuncio("encerrado", "Elétrica", 1);
        encerrado.setStatus(StatusAnuncio.ENCERRADO);
        anuncioRepository.save(encerrado);
        Anuncio comEscolhido = anuncio("escolhido", "Elétrica", 1);
        candidatura(comEscolhido, usuario("Joana Lima", "joana@example.com", "10433218100", PapelUsuario.PRESTADOR, true, true),
                StatusAplicacao.ESCOLHIDA);

        for (Anuncio negado : List.of(proprio, encerrado, comEscolhido)) {
            detalhar(rafael, negado.getId()).andExpect(status().isNotFound());
        }
    }

    @Test
    void dadoCandidaturaEmAnuncioQueJaTemEscolhido_quandoDetalha_entaoAindaAcompanha() throws Exception {
        Usuario rafael = prestador();
        perfil(rafael, 10, "Elétrica");
        Anuncio anuncio = anuncio("escolhido-outro", "Elétrica", 1);
        candidatura(anuncio, rafael, StatusAplicacao.RECUSADA);
        candidatura(anuncio, usuario("Joana Lima", "joana@example.com", "10433218100", PapelUsuario.PRESTADOR, true, true),
                StatusAplicacao.ESCOLHIDA);

        detalhar(rafael, anuncio.getId()).andExpect(status().isOk()).andExpect(jsonPath("$.minhaCandidatura", is("RECUSADA")));
    }

    @Test
    void dadoDoisPrestadoresComPerfilIgual_quandoDetalham_entaoRecebemOMesmoConteudo() throws Exception {
        Usuario rafael = prestador();
        Usuario joana = usuario("Joana Lima", "joana@example.com", "10433218100", PapelUsuario.PRESTADOR, true, true);
        perfil(rafael, 10, "Elétrica");
        perfil(joana, 10, "Elétrica");
        Anuncio anuncio = anuncio("igual", "Elétrica", 2);

        String paraRafael = detalhar(rafael, anuncio.getId()).andReturn().getResponse().getContentAsString();
        String paraJoana = detalhar(joana, anuncio.getId()).andReturn().getResponse().getContentAsString();

        assertThat(paraRafael).isEqualTo(paraJoana);
    }

    @Test
    void dadoPapelEmpregador_quandoDetalha_entao403AcessoNegado() throws Exception {
        detalhar(empregador, UUID.randomUUID()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
    }

    @Test
    void dadoContatoNaoConfirmado_quandoDetalha_entao403ContatosNaoConfirmados() throws Exception {
        Usuario semEmail = usuario("Sem Email", "sememail@example.com", "39053344705", PapelUsuario.PRESTADOR, false, true);
        perfil(semEmail, 10, "Elétrica");

        detalhar(semEmail, UUID.randomUUID()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("CONTATOS_NAO_CONFIRMADOS")));
    }

    @Test
    void dadoPerfilIncompleto_quandoDetalha_entao403PerfilIncompleto() throws Exception {
        detalhar(prestador(), UUID.randomUUID()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("PERFIL_INCOMPLETO")));
    }

    @Test
    void dadoSemToken_quandoDetalha_entao401() throws Exception {
        mockMvc.perform(get(ROTA + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void dadoEnderecosAntigos_quandoMigracaoV24Roda_entaoCidadeSaiDoFimDoEnderecoDeUmaLinha() {
        String regex = ", ([^,]+) - [A-Z]{2}$";
        String sql = "select btrim(substring(? from '" + regex + "'))";

        assertThat(jdbc.queryForObject(sql, String.class, "Rua A, 10 - Bloco B, apto 2, Pinheiros, São Paulo - SP"))
                .isEqualTo("São Paulo");
        assertThat(jdbc.queryForObject(sql, String.class, "Rua A, 10, Cambuí, Campinas - SP")).isEqualTo("Campinas");
        assertThat(jdbc.queryForObject(sql, String.class, "formato desconhecido")).isNull();
    }
}
