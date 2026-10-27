package com.limio.api.modulos.perfil.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.seguranca.JwtAuthFilter;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.perfil.actions.service.Geocodificador;

/**
 * BDD ponta a ponta do acesso por papel nas rotas de endereço salvo (TICKET-0050): só o papel ativo
 * {@code EMPREGADOR} entra; Prestador recebe 403 {@code ACESSO_NEGADO} e a chamada não cria
 * {@code perfil_empregador} nem endereço. O token é emitido com o {@link JwtEncoder}, no formato lido pelo
 * {@link JwtAuthFilter}, sem depender de {@code modulos.auth}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AcessoPorPapelEnderecoSalvoStoryTest {

    private static final String ROTA = "/conta/perfil-empregador/enderecos";
    private static final String CORPO = """
            {"apelido":"Casa","cep":"01310100","rua":"Avenida Paulista","numero":"1000","bairroRegiao":"Bela Vista",
             "cidade":"São Paulo","uf":"SP"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private Geocodificador geocodificador;

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

    private ResultActions chamar(MockHttpServletRequestBuilder requisicao, PapelUsuario papel) throws Exception {
        return mockMvc.perform(requisicao
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(UUID.randomUUID(), papel))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPO));
    }

    private int total(String tabela) {
        return jdbc.queryForObject("select count(*) from " + tabela, Integer.class);
    }

    @Test
    void dadoPapelPrestador_quandoChamaQualquerRotaDeEndereco_entaoDevolve403SemCriarNada() throws Exception {
        int perfisAntes = total("perfil_empregador");
        int enderecosAntes = total("endereco_salvo");
        String id = UUID.randomUUID().toString();

        for (MockHttpServletRequestBuilder rota : new MockHttpServletRequestBuilder[] {
                get(ROTA), post(ROTA), put(ROTA + "/" + id), delete(ROTA + "/" + id),
                patch(ROTA + "/" + id + "/padrao") }) {
            chamar(rota, PapelUsuario.PRESTADOR)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
        }

        assertThat(total("perfil_empregador")).isEqualTo(perfisAntes);
        assertThat(total("endereco_salvo")).isEqualTo(enderecosAntes);
    }
}
