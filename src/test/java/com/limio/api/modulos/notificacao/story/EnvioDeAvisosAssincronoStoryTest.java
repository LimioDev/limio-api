package com.limio.api.modulos.notificacao.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.notificacao.actions.service.EmailAvisoService;
import com.limio.api.modulos.notificacao.actions.service.PushExpoService;
import com.limio.api.modulos.notificacao.actions.service.PushExpoService.ResultadoPush;

import tools.jackson.databind.ObjectMapper;

/**
 * O envio dos avisos (ETI-112) com o executor de verdade ({@code notificacao.envio.assincrono=true}): o
 * {@code @Async} + {@code AFTER_COMMIT} do {@code EntregarNotificacaoUseCase} roda numa thread do envio, depois que a
 * candidatura já respondeu. Os demais stories rodam o envio na mesma thread.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "notificacao.envio.assincrono=true")
class EnvioDeAvisosAssincronoStoryTest {

    private static final String SENHA = "senhaForte123";
    private static final String TOKEN_MARIA = "ExponentPushToken[maria-celular]";
    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private PushExpoService pushExpoService;

    @MockitoBean
    private EmailAvisoService emailAvisoService;

    @BeforeEach
    void prepararBase() {
        limparBase();
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private void limparBase() {
        jdbc.execute("delete from envio_notificacao");
        jdbc.execute("delete from notificacao");
        jdbc.execute("delete from aparelho_push");
        jdbc.execute("delete from preferencia_notificacao");
        jdbc.execute("delete from mensagem");
        jdbc.execute("delete from chat");
        jdbc.execute("delete from aplicacao");
        jdbc.execute("delete from anuncio");
        jdbc.execute("delete from cpf_bloqueado");
        jdbc.execute("TRUNCATE TABLE usuario CASCADE");
    }

    @Test
    void dadoPushLigado_quandoRecebeCandidato_entaoOPushSaiNaThreadDoEnvioEFicaRegistrado() throws Exception {
        Usuario maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        Usuario rafael = usuario("Rafael Souza", "rafael@example.com", "11144477735", PapelUsuario.PRESTADOR);
        AtomicReference<String> threadDoEnvio = new AtomicReference<>();
        when(pushExpoService.enviar(anyList(), anyString(), anyString(), anyMap())).thenAnswer(chamada -> {
            threadDoEnvio.set(Thread.currentThread().getName());
            return ResultadoPush.enviado(List.of());
        });
        mockMvc.perform(post("/notificacoes/aparelhos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar(maria))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", TOKEN_MARIA,
                                "plataforma", "ANDROID"))))
                .andExpect(status().isNoContent());
        Anuncio anuncio = anuncioRepository.save(Anuncio.builder()
                .contratanteId(maria.getId())
                .titulo("Troca de chuveiro")
                .categoriaId(UUID.randomUUID())
                .descricao("Trocar o chuveiro do banheiro")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(3)))
                .bairroRegiaoSnapshot("Perdizes")
                .status(StatusAnuncio.PUBLICADO)
                .build());

        mockMvc.perform(post("/anuncios/{id}/candidaturas", anuncio.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + logar(rafael)))
                .andExpect(status().isCreated());

        assertThat(aguardarEnvios()).containsExactly("PUSH:ENVIADO");
        assertThat(threadDoEnvio.get()).startsWith("envio-avisos-");
    }

    private List<String> aguardarEnvios() throws InterruptedException {
        Instant limite = Instant.now().plus(ESPERA_MAXIMA);
        List<String> envios = envios();
        while (envios.isEmpty() && Instant.now().isBefore(limite)) {
            Thread.sleep(50);
            envios = envios();
        }
        return envios;
    }

    private List<String> envios() {
        return jdbc.queryForList("select canal || ':' || status from envio_notificacao", String.class);
    }

    private Usuario usuario(String nome, String email, String cpf, PapelUsuario papel) {
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
                .emailConfirmadoEm(Instant.now())
                .build());
    }

    private String logar(Usuario usuario) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", usuario.getEmail(), "senha", SENHA,
                                "dispositivo", "celular"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("token").asString();
    }
}
