package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.ImagensDeTeste;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.seguranca.JwtAuthFilter;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.perfil.actions.service.Geocodificador;
import com.limio.api.modulos.perfil.geo.Coordenadas;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do ETI-59 — criar e publicar anúncio, anexar fotos e consultar o
 * próprio anúncio. Geocodificador e bucket são mocks: os testes não dependem de rede, e dá pra conferir o que
 * seria enviado ao storage.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CriarEPublicarAnuncioStoryTest {

    private static final String ROTA = "/anuncios";
    private static final String SENHA = "senhaForte123";
    private static final Coordenadas PAULISTA = new Coordenadas(-23.5614, -46.6559);
    private static final LinkTemporario LINK = new LinkTemporario("https://storage.test/assinado",
            Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS));
    private static final Map<String, Object> ENDERECO_AVULSO = Map.of(
            "cep", "01310-100", "rua", "Avenida Paulista", "numero", "1000", "complemento", "Sala 5",
            "bairroRegiao", "Bela Vista", "cidade", "São Paulo", "uf", "SP");

    private static final Conta MARIA = new Conta("Maria da Silva", "maria@example.com", "11999998888", "52998224725");
    private static final Conta JOAO = new Conta("João Pereira", "joao@example.com", "21988887777", "11144477735");

    private record Conta(String nomeCompleto, String email, String telefone, String cpf) {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    @MockitoBean
    private Geocodificador geocodificador;

    @MockitoBean
    private ArmazenamentoService armazenamentoService;

    @BeforeEach
    void prepararBase() {
        limparBase();
        when(geocodificador.localizarEndereco(any())).thenReturn(Optional.of(PAULISTA));
        when(armazenamentoService.gerarLink(anyString())).thenReturn(LINK);
    }

    /** Desde a confirmação de e-mail (TICKET-0047), o cadastro grava token em verificacao_contato. */
    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private void limparBase() {
        jdbc.execute("delete from midia");
        jdbc.execute("delete from aplicacao");
        jdbc.execute("delete from anuncio");
        jdbc.execute("delete from endereco_salvo");
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from verificacao_contato");
        jdbc.execute("delete from sessao");
        jdbc.execute("delete from tentativa_login");
        jdbc.execute("delete from usuario");
    }

    // ---------- criar ----------

    @Test
    void dadoEnderecoSalvo_quandoCria_entao201PublicadoComRetratoDoEndereco() throws Exception {
        String token = empregadorConfirmado(MARIA);
        String enderecoSalvoId = criarEnderecoSalvo(token);

        String corpo = criar(token, corpo(Map.of("enderecoSalvoId", enderecoSalvoId)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/anuncios/")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("PUBLICADO")))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> anuncio = anuncio(objectMapper.readTree(corpo).get("id").asString());
        assertThat(anuncio.get("contratante_id")).isEqualTo(usuarioIdDe(MARIA));
        assertThat(anuncio.get("categoria_id")).isEqualTo(categoriaAtiva());
        assertThat(anuncio.get("endereco_salvo_id")).hasToString(enderecoSalvoId);
        assertThat(anuncio.get("bairro_regiao_snapshot")).isEqualTo("Pinheiros");
        assertThat(anuncio.get("endereco_completo_snapshot"))
                .isEqualTo("Rua dos Pinheiros, 100, Pinheiros, São Paulo - SP");
        assertThat(anuncio.get("latitude_snapshot")).isEqualTo(PAULISTA.latitude());
        assertThat(anuncio.get("prazo_validacao_horas")).isEqualTo(24);
        assertThat(anuncio.get("status")).isEqualTo("PUBLICADO");
    }

    @Test
    void dadoEnderecoAvulso_quandoCria_entao201ComCoordenadasDoGeocodificadorESemEnderecoSalvo() throws Exception {
        String token = empregadorConfirmado(MARIA);

        String corpo = criar(token, corpo(Map.of("enderecoAvulso", ENDERECO_AVULSO)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> anuncio = anuncio(objectMapper.readTree(corpo).get("id").asString());
        assertThat(anuncio.get("endereco_salvo_id")).isNull();
        assertThat(anuncio.get("bairro_regiao_snapshot")).isEqualTo("Bela Vista");
        assertThat(anuncio.get("endereco_completo_snapshot"))
                .isEqualTo("Avenida Paulista, 1000 - Sala 5, Bela Vista, São Paulo - SP");
        assertThat(anuncio.get("latitude_snapshot")).isEqualTo(PAULISTA.latitude());
        assertThat(anuncio.get("longitude_snapshot")).isEqualTo(PAULISTA.longitude());
    }

    @Test
    void dadoDataNoPassado_quandoCria_entao422DataNoPassadoENadaGravado() throws Exception {
        String token = empregadorConfirmado(MARIA);
        Map<String, Object> campos = new HashMap<>(Map.of("enderecoAvulso", ENDERECO_AVULSO));
        campos.put("dataHoraPretendida", Instant.now().minus(Duration.ofHours(1)).toString());

        criar(token, corpo(campos))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo", is("DATA_NO_PASSADO")));
        assertThat(totalAnuncios()).isZero();
    }

    @Test
    void dadoContatosNaoConfirmados_quandoCria_entao403ContatosNaoConfirmados() throws Exception {
        String token = cadastrarELogar(MARIA);

        criar(token, corpo(Map.of("enderecoAvulso", ENDERECO_AVULSO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("CONTATOS_NAO_CONFIRMADOS")));
        assertThat(totalAnuncios()).isZero();
        verify(geocodificador, never()).localizarEndereco(any());
    }

    @Test
    void dadoPapelPrestador_quandoCria_entao403AcessoNegado() throws Exception {
        empregadorConfirmado(MARIA);
        String tokenPrestador = token(usuarioIdDe(MARIA), PapelUsuario.PRESTADOR);

        criar(tokenPrestador, corpo(Map.of("enderecoAvulso", ENDERECO_AVULSO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
        assertThat(totalAnuncios()).isZero();
    }

    @Test
    void dadoEnderecoSalvoEAvulsoJuntos_quandoCria_entao400() throws Exception {
        String token = empregadorConfirmado(MARIA);
        String enderecoSalvoId = criarEnderecoSalvo(token);

        criar(token, corpo(Map.of("enderecoSalvoId", enderecoSalvoId, "enderecoAvulso", ENDERECO_AVULSO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoSemEndereco_quandoCria_entao400() throws Exception {
        String token = empregadorConfirmado(MARIA);

        criar(token, corpo(Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    /** Card: sem título, com menos de 5 ou mais de 80 (sem contar os espaços das pontas) é recusado. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "      ", "abcd", "  abcd  ", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" })
    void dadoTituloInvalido_quandoCria_entao400(String titulo) throws Exception {
        String token = empregadorConfirmado(MARIA);
        Map<String, Object> campos = new HashMap<>(Map.of("enderecoAvulso", ENDERECO_AVULSO));
        campos.put("titulo", titulo);

        criar(token, corpo(campos))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoDescricaoAcimaDe600Caracteres_quandoCria_entao400() throws Exception {
        String token = empregadorConfirmado(MARIA);
        Map<String, Object> campos = new HashMap<>(Map.of("enderecoAvulso", ENDERECO_AVULSO));
        campos.put("descricao", "a".repeat(601));

        criar(token, corpo(campos))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoEnderecoSalvoDeOutroEmpregador_quandoCria_entao404() throws Exception {
        String enderecoDoJoao = criarEnderecoSalvo(empregadorConfirmado(JOAO));
        String token = empregadorConfirmado(MARIA);

        criar(token, corpo(Map.of("enderecoSalvoId", enderecoDoJoao)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ENDERECO_NAO_ENCONTRADO")));
        assertThat(totalAnuncios()).isZero();
    }

    @Test
    void dadoCategoriaInativa_quandoCria_entao422CategoriaInvalida() throws Exception {
        String token = empregadorConfirmado(MARIA);
        UUID inativa = jdbc.queryForObject(
                "update categoria set ativa = false where id = (select id from categoria where not protegida"
                        + " order by ordem limit 1) returning id", UUID.class);
        try {
            Map<String, Object> campos = new HashMap<>(Map.of("enderecoAvulso", ENDERECO_AVULSO));
            campos.put("categoriaId", inativa.toString());

            criar(token, corpo(campos))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.codigo", is("CATEGORIA_INVALIDA")));
        } finally {
            jdbc.update("update categoria set ativa = true where id = ?", inativa);
        }
    }

    // ---------- fotos ----------

    @Test
    void dadoFotosValidas_quandoAnexa_entao200ComAsFotosEMidiaDoAnuncioSemExif() throws Exception {
        String token = empregadorConfirmado(MARIA);
        String anuncioId = criarAnuncio(token);

        anexar(token, anuncioId, foto("a.jpg", "image/jpeg", ImagensDeTeste.jpegComExif(40, 20, 1)),
                foto("b.png", "image/png", ImagensDeTeste.png(30, 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].url", is(LINK.url())));

        assertThat(jdbc.queryForObject(
                "select count(*) from midia where tipo = 'FOTO_ANUNCIO' and anuncio_id = ?::uuid and exif_removido",
                Integer.class, anuncioId)).isEqualTo(2);
        verify(armazenamentoService).enviar(startsWith("fotos-anuncio/" + anuncioId + "/"), any(),
                eq("image/jpeg"));
    }

    @Test
    void dadoArquivoQueNaoEImagem_quandoAnexa_entao422FotoInvalidaENadaEnviado() throws Exception {
        String token = empregadorConfirmado(MARIA);
        String anuncioId = criarAnuncio(token);

        anexar(token, anuncioId, foto("a.gif", "image/gif", ImagensDeTeste.gif(10, 10)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo", is("FOTO_INVALIDA")));
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
        assertThat(totalMidias()).isZero();
    }

    @Test
    void dadoCandidaturaEscolhida_quandoAnexa_entao409AnuncioEstadoInvalido() throws Exception {
        String token = empregadorConfirmado(MARIA);
        String anuncioId = criarAnuncio(token);
        candidatura(UUID.fromString(anuncioId), UUID.randomUUID(), StatusAplicacao.ESCOLHIDA);

        anexar(token, anuncioId, foto("a.png", "image/png", ImagensDeTeste.png(10, 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_ESTADO_INVALIDO")));
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
    }

    @Test
    void dadoAnuncioDeOutraPessoa_quandoAnexa_entao404() throws Exception {
        String anuncioDoJoao = criarAnuncio(empregadorConfirmado(JOAO));
        String token = empregadorConfirmado(MARIA);

        anexar(token, anuncioDoJoao, foto("a.png", "image/png", ImagensDeTeste.png(10, 10)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_NAO_ENCONTRADO")));
    }

    @Test
    void dadoLimiteDeFotos_quandoAnexaASexta_entao422() throws Exception {
        String token = empregadorConfirmado(MARIA);
        String anuncioId = criarAnuncio(token);
        MockMultipartFile[] cinco = new MockMultipartFile[5];
        for (int i = 0; i < cinco.length; i++) {
            cinco[i] = foto("f" + i + ".png", "image/png", ImagensDeTeste.png(10 + i, 10));
        }
        anexar(token, anuncioId, cinco).andExpect(status().isOk());

        anexar(token, anuncioId, foto("f6.png", "image/png", ImagensDeTeste.png(9, 9)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo", is("FOTO_INVALIDA")));
        assertThat(totalMidias()).isEqualTo(5);
    }

    // ---------- consultar ----------

    @Test
    void dadoAnuncioComFotoECandidato_quandoDonoConsulta_entaoVemEnderecoCompletoFotosECandidatos() throws Exception {
        cadastrarELogar(JOAO);
        String token = empregadorConfirmado(MARIA);
        String anuncioId = criarAnuncio(token);
        anexar(token, anuncioId, foto("a.png", "image/png", ImagensDeTeste.png(10, 10))).andExpect(status().isOk());
        candidatura(UUID.fromString(anuncioId), usuarioIdDe(JOAO), StatusAplicacao.ENVIADA);

        consultar(token, anuncioId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(anuncioId)))
                .andExpect(jsonPath("$.status", is("PUBLICADO")))
                .andExpect(jsonPath("$.titulo", is("Faxina do apartamento")))
                .andExpect(jsonPath("$.descricao", is("Faxina completa do apartamento")))
                .andExpect(jsonPath("$.categoria.id", is(categoriaAtiva().toString())))
                .andExpect(jsonPath("$.categoria.grupo", notNullValue()))
                .andExpect(jsonPath("$.bairroRegiao", is("Bela Vista")))
                .andExpect(jsonPath("$.uf", is("SP")))
                .andExpect(jsonPath("$.enderecoCompleto", endsWith("São Paulo - SP")))
                .andExpect(jsonPath("$.tamanhoLocal", is("72 m²")))
                .andExpect(jsonPath("$.numeroPessoas", is(2)))
                .andExpect(jsonPath("$.fotos", hasSize(1)))
                .andExpect(jsonPath("$.candidatos", is(1)))
                .andExpect(jsonPath("$.primeirosCandidatos[0].nome", is("João P.")))
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.latitudeSnapshot").doesNotExist());
    }

    @Test
    void dadoAnuncioDeOutraPessoa_quandoConsulta_entao404() throws Exception {
        String anuncioDoJoao = criarAnuncio(empregadorConfirmado(JOAO));
        String token = empregadorConfirmado(MARIA);

        consultar(token, anuncioDoJoao)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("ANUNCIO_NAO_ENCONTRADO")));
    }

    // ---------- apoio ----------

    private String corpo(Map<String, Object> extras) throws Exception {
        Map<String, Object> campos = new HashMap<>();
        campos.put("categoriaId", categoriaAtiva().toString());
        campos.put("titulo", "  Faxina do apartamento  ");
        campos.put("descricao", "Faxina completa do apartamento");
        campos.put("dataHoraPretendida", Instant.now().plus(Duration.ofDays(3)).toString());
        campos.put("tamanhoLocal", "72 m²");
        campos.put("numeroPessoas", 2);
        campos.putAll(extras);
        return objectMapper.writeValueAsString(campos);
    }

    private ResultActions criar(String token, String corpo) throws Exception {
        return mockMvc.perform(post(ROTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private String criarAnuncio(String token) throws Exception {
        String corpo = criar(token, corpo(Map.of("enderecoAvulso", ENDERECO_AVULSO)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asString();
    }

    private ResultActions anexar(String token, String anuncioId, MockMultipartFile... fotos) throws Exception {
        var requisicao = multipart(ROTA + "/" + anuncioId + "/fotos");
        for (MockMultipartFile foto : fotos) {
            requisicao.file(foto);
        }
        return mockMvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions consultar(String token, String anuncioId) throws Exception {
        return mockMvc.perform(get(ROTA + "/" + anuncioId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static MockMultipartFile foto(String nome, String tipo, byte[] conteudo) {
        return new MockMultipartFile("arquivos", nome, tipo, conteudo);
    }

    private void candidatura(UUID anuncioId, UUID prestadorId, StatusAplicacao status) {
        aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(prestadorId)
                .status(status)
                .dataAplicacao(Instant.now())
                .build());
    }

    private String criarEnderecoSalvo(String token) throws Exception {
        String corpo = mockMvc.perform(post("/conta/perfil-empregador/enderecos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "apelido", "Casa", "cep", "05422000", "rua", "Rua dos Pinheiros", "numero", "100",
                                "bairroRegiao", "Pinheiros", "cidade", "São Paulo", "uf", "SP"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asString();
    }

    private String empregadorConfirmado(Conta conta) throws Exception {
        String token = cadastrarELogar(conta);
        jdbc.update("update usuario set email_confirmado_em = now(), telefone_confirmado_em = now() where email = ?",
                conta.email());
        return token;
    }

    private String cadastrarELogar(Conta conta) throws Exception {
        mockMvc.perform(post("/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nomeCompleto", conta.nomeCompleto(),
                                "email", conta.email(),
                                "telefone", conta.telefone(),
                                "senha", SENHA,
                                "cpf", conta.cpf(),
                                "dataNascimento", "1990-01-01",
                                "cidadeUf", "São Paulo/SP"))))
                .andExpect(status().isCreated());
        String corpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", conta.email(), "senha", SENHA, "dispositivo", "celular"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("token").asString();
    }

    /** Token com outro papel ativo, no formato lido pelo {@link JwtAuthFilter}. */
    private String token(UUID usuarioId, PapelUsuario papel) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuarioId.toString())
                .claim(JwtAuthFilter.CLAIM_PAPEL, papel.name())
                .issuedAt(agora)
                .expiresAt(agora.plus(5, ChronoUnit.MINUTES))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private UUID usuarioIdDe(Conta conta) {
        return jdbc.queryForObject("select id from usuario where email = ?", UUID.class, conta.email());
    }

    private UUID categoriaAtiva() {
        return jdbc.queryForObject("select id from categoria where protegida", UUID.class);
    }

    private Map<String, Object> anuncio(String id) {
        return jdbc.queryForMap("select * from anuncio where id = ?::uuid", id);
    }

    private int totalAnuncios() {
        return jdbc.queryForObject("select count(*) from anuncio", Integer.class);
    }

    private int totalMidias() {
        return jdbc.queryForObject("select count(*) from midia", Integer.class);
    }
}
