package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.NovoServicoNaAreaEvent;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.perfil.actions.service.Geocodificador;
import com.limio.api.modulos.perfil.geo.Coordenadas;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta do aviso aos Prestadores compatíveis (ETI-65): o Empregador publica pela rota real ({@code POST
 * /anuncios}), o commit dispara o listener e os pedidos de aviso ({@link NovoServicoNaAreaEvent}) são conferidos com
 * {@link RecordApplicationEvents}. O anúncio fica em Perdizes (geocodificador mockado); os perfis entram por SQL.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class AvisarPrestadoresCompativeisStoryTest {

    private static final String SENHA = "senhaForte123";
    private static final Coordenadas PERDIZES = new Coordenadas(-23.5372, -46.6815);
    /** Avenida Paulista: uns 3,7 km de Perdizes. */
    private static final Coordenadas PAULISTA = new Coordenadas(-23.5614, -46.6559);
    /** Campinas: uns 90 km de Perdizes. */
    private static final Coordenadas CAMPINAS = new Coordenadas(-22.9, -47.06);
    private static final Map<String, Object> ENDERECO_AVULSO = Map.of(
            "cep", "05015-000", "rua", "Rua Cardoso de Almeida", "numero", "100",
            "bairroRegiao", "Perdizes", "cidade", "São Paulo", "uf", "SP");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents eventos;

    @MockitoBean
    private Geocodificador geocodificador;

    private Usuario maria;

    @BeforeEach
    void prepararBase() {
        limparBase();
        when(geocodificador.localizarEndereco(any())).thenReturn(Optional.of(PERDIZES));
        maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR, true);
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private void limparBase() {
        jdbc.execute("delete from midia");
        jdbc.execute("delete from aplicacao");
        jdbc.execute("delete from anuncio");
        jdbc.execute("delete from prestador_categoria");
        jdbc.execute("delete from janela_atendimento");
        jdbc.execute("delete from perfil_prestador");
        jdbc.execute("delete from endereco_salvo");
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from verificacao_contato");
        jdbc.execute("delete from sessao");
        jdbc.execute("delete from tentativa_login");
        jdbc.execute("delete from usuario");
    }

    // ---------- quem recebe ----------

    @Test
    void dadoPrestadorComAEspecialidadeNoRaio_quandoAnuncioPublicado_entaoRecebeAvisoComBairroEDistancia() throws Exception {
        Usuario rafael = prestador("rafael@example.com", "11144477735");
        perfil(rafael, PAULISTA, 10, true, categoria(0));

        publicar(categoria(0));

        List<NovoServicoNaAreaEvent> avisos = avisos();
        assertThat(avisos).hasSize(1);
        NovoServicoNaAreaEvent aviso = avisos.get(0);
        assertThat(aviso.destinatarioId()).isEqualTo(rafael.getId());
        assertThat(aviso.anuncioTitulo()).isEqualTo("Troca de chuveiro");
        assertThat(aviso.categoriaNome()).isEqualTo(nomeDa(categoria(0)));
        assertThat(aviso.bairroRegiao()).isEqualTo("Perdizes");
        assertThat(aviso.distanciaKm()).isCloseTo(3.7, within(0.3));
        assertThat(aviso.anuncioId()).isEqualTo(anuncioPublicado());
    }

    @Test
    void dadoAnuncioEmOutros_quandoPublicado_entaoAvisaPrestadorDeQualquerEspecialidadeNoRaio() throws Exception {
        Usuario rafael = prestador("rafael@example.com", "11144477735");
        perfil(rafael, PAULISTA, 10, true, categoria(0));

        publicar(outros());

        assertThat(destinatarios()).containsExactly(rafael.getId());
    }

    @Test
    void dadoPrestadorComOutros_quandoAnuncioDeOutraCategoriaPublicado_entaoRecebeAviso() throws Exception {
        Usuario rafael = prestador("rafael@example.com", "11144477735");
        perfil(rafael, PAULISTA, 10, true, outros());

        publicar(categoria(0));

        assertThat(destinatarios()).containsExactly(rafael.getId());
    }

    /** Decisão do ETI-65: "papel apto" é ter o perfil de Prestador; quem alternou pra Empregador ainda recebe. */
    @Test
    void dadoPrestadorNavegandoComoEmpregador_quandoAnuncioPublicado_entaoRecebeAviso() throws Exception {
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "11144477735", PapelUsuario.EMPREGADOR, true);
        perfil(rafael, PAULISTA, 10, true, categoria(0));

        publicar(categoria(0));

        assertThat(destinatarios()).containsExactly(rafael.getId());
    }

    @Test
    void dadoVariosCompativeis_quandoAnuncioPublicado_entaoUmAvisoPorPrestadorDoMaisPertoProMaisLonge() throws Exception {
        Usuario rafael = prestador("rafael@example.com", "11144477735");
        Usuario pedro = prestador("pedro@example.com", "10433218100");
        perfil(rafael, PAULISTA, 10, true, categoria(0));
        perfil(pedro, PERDIZES, 10, true, categoria(0));

        publicar(categoria(0));

        assertThat(destinatarios()).containsExactly(pedro.getId(), rafael.getId());
    }

    // ---------- quem não recebe ----------

    @Test
    void dadoPrestadorDeOutraEspecialidadeSemOutros_quandoAnuncioPublicado_entaoNaoRecebe() throws Exception {
        Usuario rafael = prestador("rafael@example.com", "11144477735");
        perfil(rafael, PAULISTA, 10, true, categoria(1));

        publicar(categoria(0));

        assertThat(avisos()).isEmpty();
    }

    @Test
    void dadoPrestadorForaDoRaio_quandoAnuncioPublicado_entaoNaoRecebeEAnuncioContinuaPublicado() throws Exception {
        Usuario rafael = prestador("rafael@example.com", "11144477735");
        perfil(rafael, CAMPINAS, 10, true, categoria(0));

        publicar(categoria(0));

        assertThat(avisos()).isEmpty();
        assertThat(jdbc.queryForObject("select status from anuncio", String.class)).isEqualTo("PUBLICADO");
    }

    @Test
    void dadoRaioMenorQueADistancia_quandoAnuncioPublicado_entaoNaoRecebe() throws Exception {
        Usuario rafael = prestador("rafael@example.com", "11144477735");
        perfil(rafael, PAULISTA, 3, true, categoria(0));

        publicar(categoria(0));

        assertThat(avisos()).as("3,7 km > raio de 3 km").isEmpty();
    }

    @Test
    void dadoPerfilIncompleto_quandoAnuncioPublicado_entaoNaoRecebe() throws Exception {
        Usuario rafael = prestador("rafael@example.com", "11144477735");
        perfil(rafael, PAULISTA, 10, false, categoria(0));

        publicar(categoria(0));

        assertThat(avisos()).isEmpty();
    }

    @Test
    void dadoDonoDoAnuncioComPerfilDePrestadorCompativel_quandoPublica_entaoNaoRecebeAvisoDoProprioAnuncio()
            throws Exception {
        perfil(maria, PERDIZES, 10, true, categoria(0));

        publicar(categoria(0));

        assertThat(avisos()).isEmpty();
    }

    @Test
    void dadoPrestadorSemEmailConfirmado_quandoAnuncioPublicado_entaoNaoRecebe() throws Exception {
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "11144477735", PapelUsuario.PRESTADOR, false);
        perfil(rafael, PAULISTA, 10, true, categoria(0));

        publicar(categoria(0));

        assertThat(avisos()).isEmpty();
    }

    @Test
    void dadoPrestadorSuspenso_quandoAnuncioPublicado_entaoNaoRecebe() throws Exception {
        Usuario rafael = prestador("rafael@example.com", "11144477735");
        perfil(rafael, PAULISTA, 10, true, categoria(0));
        jdbc.update("update usuario set status_conta = 'BLOQUEADA' where id = ?", rafael.getId());

        publicar(categoria(0));

        assertThat(avisos()).isEmpty();
    }

    // ---------- apoio ----------

    private Usuario prestador(String email, String cpf) {
        return usuario("Rafael Souza", email, cpf, PapelUsuario.PRESTADOR, true);
    }

    private Usuario usuario(String nome, String email, String cpf, PapelUsuario papel, boolean emailConfirmado) {
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
                .emailConfirmadoEm(emailConfirmado ? Instant.now() : null)
                .build());
    }

    private void perfil(Usuario usuario, Coordenadas base, int raioKm, boolean completo, UUID especialidade) {
        UUID perfilId = UUID.randomUUID();
        jdbc.update("""
                insert into perfil_prestador (id, usuario_id, raio_atendimento_km, municipio_base, uf_base,
                    latitude_base, longitude_base, completo)
                values (?, ?, ?, 'São Paulo', 'SP', ?, ?, ?)""", perfilId, usuario.getId(), raioKm,
                base.latitude(), base.longitude(), completo);
        jdbc.update("insert into prestador_categoria (perfil_prestador_id, categoria_id) values (?, ?)", perfilId,
                especialidade);
    }

    /** Categorias comuns (não "Outros"), ativas, em ordem estável. */
    private UUID categoria(int indice) {
        return jdbc.queryForList("select id from categoria where not protegida and ativa order by nome, id",
                UUID.class).get(indice);
    }

    private UUID outros() {
        return jdbc.queryForObject("select id from categoria where protegida", UUID.class);
    }

    private String nomeDa(UUID categoriaId) {
        return jdbc.queryForObject("select nome from categoria where id = ?", String.class, categoriaId);
    }

    private UUID anuncioPublicado() {
        return jdbc.queryForObject("select id from anuncio", UUID.class);
    }

    private void publicar(UUID categoriaId) throws Exception {
        Map<String, Object> campos = new HashMap<>();
        campos.put("categoriaId", categoriaId.toString());
        campos.put("titulo", "Troca de chuveiro");
        campos.put("descricao", "Trocar o chuveiro do banheiro social");
        campos.put("dataHoraPretendida", Instant.now().plus(Duration.ofDays(3)).toString());
        campos.put("enderecoAvulso", ENDERECO_AVULSO);
        mockMvc.perform(post("/anuncios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar(maria.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(campos)))
                .andExpect(status().isCreated());
    }

    private String logar(String email) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, SENHA, "celular"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    private List<NovoServicoNaAreaEvent> avisos() {
        return eventos.stream(NovoServicoNaAreaEvent.class).toList();
    }

    private List<UUID> destinatarios() {
        return avisos().stream().map(NovoServicoNaAreaEvent::destinatarioId).toList();
    }
}
