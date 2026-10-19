package com.limio.api.modulos.perfil.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.modulos.perfil.actions.service.ArmazenamentoService;
import com.limio.api.modulos.perfil.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.modulos.perfil.midia.LinkTemporario;
import com.limio.api.modulos.perfil.spec.ImagensDeTeste;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do TICKET-0056 — enviar e trocar a foto de perfil.
 * O bucket é substituído por um mock ({@code ArmazenamentoService}): os testes não dependem de rede
 * nem de credencial, e dá pra conferir o que seria enviado e apagado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FotoPerfilStoryTest {

    private static final String ROTA = "/conta/foto-perfil";
    private static final String SENHA = "senhaForte123";
    private static final LinkTemporario LINK = new LinkTemporario("https://storage.test/assinado",
            Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS));

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

    @MockitoBean
    private ArmazenamentoService armazenamentoService;

    @BeforeEach
    void limparBase() {
        jdbc.execute("delete from midia");
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from verificacao_contato");
        jdbc.execute("delete from sessao");
        jdbc.execute("delete from tentativa_login");
        jdbc.execute("delete from usuario");
        when(armazenamentoService.gerarLink(anyString())).thenReturn(LINK);
    }

    @Test
    void dadoFotoValidaComLocalizacao_quandoEnvia_entao200ComOLinkEMidiaGravadaSemExif() throws Exception {
        String token = cadastrarELogar(MARIA);

        enviar(token, "foto.jpg", "image/jpeg", ImagensDeTeste.jpegComExif(40, 20, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", is(LINK.url())))
                .andExpect(jsonPath("$.expiraEm", notNullValue()));

        ArgumentCaptor<String> chave = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> conteudo = ArgumentCaptor.forClass(byte[].class);
        verify(armazenamentoService).enviar(chave.capture(), conteudo.capture(), eq("image/jpeg"));
        assertThat(ImagensDeTeste.contem(conteudo.getValue(), ImagensDeTeste.SEGREDO_EXIF)).isFalse();

        Map<String, Object> midia = jdbc.queryForMap("select * from midia where usuario_id = ?", usuarioIdDe(MARIA));
        assertThat(midia.get("tipo")).isEqualTo("FOTO_PERFIL");
        assertThat(midia.get("url_s3")).isEqualTo(chave.getValue());
        assertThat(midia.get("mime")).isEqualTo("image/jpeg");
        assertThat(midia.get("largura")).isEqualTo(40);
        assertThat(midia.get("altura")).isEqualTo(20);
        assertThat(midia.get("bytes")).isEqualTo((long) conteudo.getValue().length);
        assertThat(midia.get("exif_removido")).isEqualTo(true);
        assertThat(midia.get("expurgo_em")).isNull();
        verify(armazenamentoService, never()).remover(anyString());
    }

    @Test
    void dadoFotoAnterior_quandoTroca_entaoAAnteriorEExpurgadaEApagadaDoStorage() throws Exception {
        String token = cadastrarELogar(MARIA);
        enviar(token, "primeira.jpg", "image/jpeg", ImagensDeTeste.jpeg(40, 20)).andExpect(status().isOk());
        String chaveAntiga = jdbc.queryForObject("select url_s3 from midia", String.class);

        enviar(token, "segunda.png", "image/png", ImagensDeTeste.png(30, 30)).andExpect(status().isOk());

        List<Map<String, Object>> midias = jdbc.queryForList(
                "select url_s3, mime, expurgo_em from midia where usuario_id = ? order by criado_em",
                usuarioIdDe(MARIA));
        assertThat(midias).hasSize(2);
        assertThat(midias.get(0).get("url_s3")).isEqualTo(chaveAntiga);
        assertThat(midias.get(0).get("expurgo_em")).isNotNull();
        assertThat(midias.get(1).get("mime")).isEqualTo("image/png");
        assertThat(midias.get(1).get("expurgo_em")).isNull();
        // Depois do commit, o objeto antigo sai do bucket.
        verify(armazenamentoService).remover(chaveAntiga);
        verify(armazenamentoService, never()).remover((String) midias.get(1).get("url_s3"));
    }

    @Test
    void dadoTipoInvalido_quandoEnvia_entao422FotoInvalidaENadaGuardado() throws Exception {
        String token = cadastrarELogar(MARIA);

        enviar(token, "animacao.gif", "image/gif", ImagensDeTeste.gif(20, 20))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("FOTO_INVALIDA")))
                .andExpect(jsonPath("$.mensagem", is("Envie uma foto JPG ou PNG.")));
        // Extensão e Content-Type de imagem não bastam: o conteúdo é o que vale.
        enviar(token, "foto.jpg", "image/jpeg", "não sou uma foto".getBytes())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("FOTO_INVALIDA")));

        assertThat(totalMidias()).isZero();
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
    }

    @Test
    void dadoArquivoGrandeDemais_quandoEnvia_entao422FotoInvalidaENadaGuardado() throws Exception {
        String token = cadastrarELogar(MARIA);
        byte[] seisMega = new byte[6 * 1024 * 1024];

        enviar(token, "enorme.jpg", "image/jpeg", seisMega)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("FOTO_INVALIDA")))
                .andExpect(jsonPath("$.mensagem", is("A foto deve ter no máximo 5 MB.")));

        assertThat(totalMidias()).isZero();
        verify(armazenamentoService, never()).enviar(anyString(), any(), anyString());
    }

    @Test
    void dadoUsuarioSemFoto_quandoConsulta_entao204EDepoisDoEnvio200ComOLink() throws Exception {
        String token = cadastrarELogar(MARIA);

        consultar(token).andExpect(status().isNoContent());

        enviar(token, "foto.jpg", "image/jpeg", ImagensDeTeste.jpeg(40, 20)).andExpect(status().isOk());
        String chave = jdbc.queryForObject("select url_s3 from midia", String.class);

        consultar(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", is(LINK.url())));
        verify(armazenamentoService, atLeastOnce()).gerarLink(chave);
    }

    @Test
    void dadoDoisUsuarios_quandoUmTrocaAFoto_entaoAFotoDoOutroNaoMuda() throws Exception {
        String tokenMaria = cadastrarELogar(MARIA);
        String tokenJoao = cadastrarELogar(JOAO);
        enviar(tokenJoao, "joao.jpg", "image/jpeg", ImagensDeTeste.jpeg(20, 20)).andExpect(status().isOk());

        enviar(tokenMaria, "maria.jpg", "image/jpeg", ImagensDeTeste.jpeg(20, 20)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select count(*) from midia where usuario_id = ? and expurgo_em is null", Integer.class,
                usuarioIdDe(JOAO))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from midia where usuario_id = ? and expurgo_em is null", Integer.class,
                usuarioIdDe(MARIA))).isEqualTo(1);
        verify(armazenamentoService, never()).remover(anyString());
    }

    @Test
    void dadoStorageFora_quandoEnvia_entao503EAFotoAtualContinua() throws Exception {
        String token = cadastrarELogar(MARIA);
        enviar(token, "foto.jpg", "image/jpeg", ImagensDeTeste.jpeg(20, 20)).andExpect(status().isOk());
        doThrow(new ArmazenamentoIndisponivelException()).when(armazenamentoService)
                .enviar(anyString(), any(), anyString());

        enviar(token, "nova.jpg", "image/jpeg", ImagensDeTeste.jpeg(20, 20))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo", is("ARMAZENAMENTO_INDISPONIVEL")));

        assertThat(totalMidias()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select expurgo_em from midia", Instant.class)).isNull();
        verify(armazenamentoService, never()).remover(anyString());
    }

    @Test
    void dadoSemArquivo_quandoEnvia_entao400() throws Exception {
        String token = cadastrarELogar(MARIA);

        mockMvc.perform(multipart(HttpMethod.PUT, ROTA)
                .file(new MockMultipartFile("outro", "x.jpg", "image/jpeg", ImagensDeTeste.jpeg(10, 10)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dadoSemToken_quandoEnviaOuConsulta_entao401() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, ROTA)
                .file(new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", ImagensDeTeste.jpeg(10, 10))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
        mockMvc.perform(get(ROTA)).andExpect(status().isUnauthorized());

        assertThat(totalMidias()).isZero();
    }

    private ResultActions enviar(String token, String nome, String contentType, byte[] conteudo) throws Exception {
        return mockMvc.perform(multipart(HttpMethod.PUT, ROTA)
                .file(new MockMultipartFile("arquivo", nome, contentType, conteudo))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions consultar(String token) throws Exception {
        return mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
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

    private UUID usuarioIdDe(Conta conta) {
        return jdbc.queryForObject("select id from usuario where email = ?", UUID.class, conta.email());
    }

    private int totalMidias() {
        return jdbc.queryForObject("select count(*) from midia", Integer.class);
    }
}
