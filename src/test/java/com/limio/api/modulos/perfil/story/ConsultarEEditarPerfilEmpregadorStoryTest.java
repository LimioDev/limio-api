package com.limio.api.modulos.perfil.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) da Parte 1 do TICKET-0048 —
 * consultar o perfil de Empregador e editar o nome de exibição. O token de
 * acesso é emitido direto pelo {@link JwtEncoder} de {@code comum/}: o módulo
 * {@code perfil} só enxerga o usuário por {@code UsuarioAutenticado}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConsultarEEditarPerfilEmpregadorStoryTest {

    private static final String ROTA = "/conta/perfil-empregador";
    private static final String ROTA_NOME = ROTA + "/nome-exibicao";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PerfilEmpregadorRepository perfilEmpregadorRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void limparBase() {
        perfilEmpregadorRepository.deleteAll();
    }

    private PerfilEmpregador criarPerfil(UUID usuarioId, String nome) {
        return perfilEmpregadorRepository.save(PerfilEmpregador.builder()
                .usuarioId(usuarioId)
                .nomeExibicao(nome)
                .build());
    }

    private String tokenDe(UUID usuarioId) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuarioId.toString())
                .claim(JwtAuthFilter.CLAIM_PAPEL, "EMPREGADOR")
                .issuedAt(agora)
                .expiresAt(agora.plus(Duration.ofMinutes(15)))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private ResultActions consultar(UUID usuarioId) throws Exception {
        return mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(usuarioId)));
    }

    private ResultActions editarNome(UUID usuarioId, String nome) throws Exception {
        return mockMvc.perform(patch(ROTA_NOME)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(usuarioId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("nomeExibicao", nome))));
    }

    @Test
    void dadoPerfilSemAvaliacoes_quandoConsulta_entaoDevolveNomeMediaNulaEQuantidadeZero() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        criarPerfil(usuarioId, "Maria");

        consultar(usuarioId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("Maria")))
                .andExpect(jsonPath("$.mediaAvaliacoes", nullValue()))
                .andExpect(jsonPath("$.qtdAvaliacoes", is(0)));
    }

    @Test
    void dadoPerfilComAvaliacoes_quandoConsulta_entaoDevolveMediaEQuantidade() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        PerfilEmpregador perfil = criarPerfil(usuarioId, "Maria");
        perfil.setMediaAvaliacoes(new BigDecimal("4.5"));
        perfil.setQtdAvaliacoes(2);
        perfilEmpregadorRepository.save(perfil);

        consultar(usuarioId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaAvaliacoes", is(4.5)))
                .andExpect(jsonPath("$.qtdAvaliacoes", is(2)));
    }

    @Test
    void dadoDoisUsuarios_quandoUmConsulta_entaoVeSoOProprioPerfil() throws Exception {
        UUID maria = UUID.randomUUID();
        UUID joao = UUID.randomUUID();
        criarPerfil(maria, "Maria");
        criarPerfil(joao, "João");

        consultar(joao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("João")));
    }

    @Test
    void dadoSemToken_quandoConsulta_entao401() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }

    @Test
    void dadoUsuarioSemPerfil_quandoConsulta_entao404ComCodigoDeErro() throws Exception {
        consultar(UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("PERFIL_EMPREGADOR_NAO_ENCONTRADO")));
    }

    @Test
    void dadoNomeValido_quandoEditaNome_entaoGravaEDevolveOPerfilAtualizado() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        criarPerfil(usuarioId, "Maria");

        editarNome(usuarioId, "  Maria Silva  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao", is("Maria Silva")));

        assertThat(perfilEmpregadorRepository.findByUsuarioId(usuarioId))
                .get().extracting(PerfilEmpregador::getNomeExibicao).isEqualTo("Maria Silva");
    }

    @Test
    void dadoDoisUsuarios_quandoUmEditaNome_entaoOutroPerfilNaoMuda() throws Exception {
        UUID maria = UUID.randomUUID();
        UUID joao = UUID.randomUUID();
        criarPerfil(maria, "Maria");
        criarPerfil(joao, "João");

        editarNome(maria, "Maria Silva").andExpect(status().isOk());

        assertThat(perfilEmpregadorRepository.findByUsuarioId(joao))
                .get().extracting(PerfilEmpregador::getNomeExibicao).isEqualTo("João");
    }

    @Test
    void dadoNomeEmBrancoOuGrande_quandoEditaNome_entao400ENomeNaoMuda() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        criarPerfil(usuarioId, "Maria");

        editarNome(usuarioId, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        editarNome(usuarioId, "a".repeat(PerfilEmpregador.NOME_EXIBICAO_MAX + 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        assertThat(perfilEmpregadorRepository.findByUsuarioId(usuarioId))
                .get().extracting(PerfilEmpregador::getNomeExibicao).isEqualTo("Maria");
    }

    @Test
    void dadoSemToken_quandoEditaNome_entao401() throws Exception {
        mockMvc.perform(patch(ROTA_NOME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("nomeExibicao", "Maria"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dadoUsuarioSemPerfil_quandoEditaNome_entao404SemCriarPerfil() throws Exception {
        UUID usuarioId = UUID.randomUUID();

        editarNome(usuarioId, "Maria")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("PERFIL_EMPREGADOR_NAO_ENCONTRADO")));

        assertThat(perfilEmpregadorRepository.findByUsuarioId(usuarioId)).isEmpty();
    }
}
