package com.limio.api.modulos.notificacao.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.seguranca.JwtAuthFilter;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacaoRepository;

/**
 * BDD ponta a ponta (controller -> banco real) da ETI-34: GET/PUT
 * {@code /conta/preferencias-notificacao}. O token de acesso é emitido com o
 * {@link JwtEncoder} de {@code comum/config} — mesmo formato lido pelo
 * {@link JwtAuthFilter} — sem depender de classe de {@code modulos.auth}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PreferenciaNotificacaoStoryTest {

    private static final String ROTA = "/conta/preferencias-notificacao";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private PreferenciaNotificacaoRepository repository;

    @BeforeEach
    void limparBase() {
        repository.deleteAll();
    }

    private String token(UUID usuarioId) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuarioId.toString())
                .claim(JwtAuthFilter.CLAIM_PAPEL, PapelUsuario.EMPREGADOR.name())
                .issuedAt(agora)
                .expiresAt(agora.plus(5, ChronoUnit.MINUTES))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private ResultActions consultar(UUID usuarioId) throws Exception {
        return mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(usuarioId)));
    }

    private ResultActions atualizar(UUID usuarioId, String corpo) throws Exception {
        return mockMvc.perform(put(ROTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(usuarioId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    @Test
    void dadoUsuarioSemPreferencia_quandoConsulta_entaoRecebe200ComPadroesDeContaNova() throws Exception {
        consultar(UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.push").value(true))
                .andExpect(jsonPath("$.email").value(false))
                .andExpect(jsonPath("$.sms").value(false))
                .andExpect(jsonPath("$.whatsapp").value(false));

        assertThat(repository.count()).isZero();
    }

    @Test
    void dadoUsuarioLogado_quandoAtualiza_entaoRecebe200ComEstadoSalvoEConsultaDevolveOMesmo() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        String corpo = "{\"push\":false,\"email\":true,\"sms\":true,\"whatsapp\":false}";

        atualizar(usuarioId, corpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.push").value(false))
                .andExpect(jsonPath("$.email").value(true))
                .andExpect(jsonPath("$.sms").value(true))
                .andExpect(jsonPath("$.whatsapp").value(false));

        consultar(usuarioId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.push").value(false))
                .andExpect(jsonPath("$.email").value(true))
                .andExpect(jsonPath("$.sms").value(true))
                .andExpect(jsonPath("$.whatsapp").value(false));
    }

    @Test
    void dadoDoisUsuarios_quandoUmAtualiza_entaoSoAsPreferenciasDeleMudam() throws Exception {
        UUID usuarioA = UUID.randomUUID();
        UUID usuarioB = UUID.randomUUID();

        atualizar(usuarioA, "{\"push\":false,\"email\":true,\"sms\":true,\"whatsapp\":true}")
                .andExpect(status().isOk());

        consultar(usuarioB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.push").value(true))
                .andExpect(jsonPath("$.email").value(false))
                .andExpect(jsonPath("$.sms").value(false))
                .andExpect(jsonPath("$.whatsapp").value(false));
        PreferenciaNotificacao doA = repository.findByUsuarioId(usuarioA).orElseThrow();
        assertThat(doA.getWhatsapp()).isTrue();
        assertThat(repository.findByUsuarioId(usuarioB)).isEmpty();
    }

    @Test
    void dadoCampoAusente_quandoAtualiza_entao400SemGravar() throws Exception {
        atualizar(UUID.randomUUID(), "{\"push\":true,\"email\":false,\"sms\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"));

        assertThat(repository.count()).isZero();
    }

    @Test
    void dadoCampoComTipoInvalido_quandoAtualiza_entao400SemGravar() throws Exception {
        atualizar(UUID.randomUUID(), "{\"push\":\"talvez\",\"email\":false,\"sms\":false,\"whatsapp\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"));

        assertThat(repository.count()).isZero();
    }

    @Test
    void dadoSemLogin_quandoConsultaOuAtualiza_entao401() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));

        mockMvc.perform(put(ROTA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"push\":true,\"email\":false,\"sms\":false,\"whatsapp\":false}"))
                .andExpect(status().isUnauthorized());

        assertThat(repository.count()).isZero();
    }
}
