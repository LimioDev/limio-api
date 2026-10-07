package com.limio.api.comum.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.limio.api.comum.config.JwtConfig;
import com.limio.api.comum.seguranca.enums.PapelUsuario;

class JwtAuthFilterTest {

    private final JwtConfig jwtConfig = new JwtConfig("segredo-de-teste-com-pelo-menos-32-bytes");
    private final JwtAuthFilter filtro = new JwtAuthFilter(jwtConfig.jwtDecoder());

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static String token(JwtEncoder encoder, UUID id, Instant expiraEm) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(id.toString())
                .claim(JwtAuthFilter.CLAIM_PAPEL, "PRESTADOR")
                .issuedAt(expiraEm.minus(Duration.ofMinutes(15)))
                .expiresAt(expiraEm)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private Authentication filtrar(String token) throws Exception {
        var request = new MockHttpServletRequest();
        if (token != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        var chain = new MockFilterChain();
        filtro.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("requisição sempre segue adiante").isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void tokenValidoViraUsuarioAutenticado() throws Exception {
        UUID id = UUID.randomUUID();

        Authentication autenticacao = filtrar(token(jwtConfig.jwtEncoder(), id, Instant.now().plusSeconds(900)));

        assertThat(autenticacao.getPrincipal()).isEqualTo(new UsuarioAutenticado(id, PapelUsuario.PRESTADOR));
    }

    @Test
    void tokenExpiradoSegueAnonimo() throws Exception {
        String expirado = token(jwtConfig.jwtEncoder(), UUID.randomUUID(), Instant.now().minus(Duration.ofMinutes(5)));

        assertThat(filtrar(expirado)).isNull();
    }

    @Test
    void tokenAssinadoComOutraChaveSegueAnonimo() throws Exception {
        JwtEncoder outraChave = new JwtConfig("outro-segredo-tambem-com-32-bytes-ou-mais").jwtEncoder();

        assertThat(filtrar(token(outraChave, UUID.randomUUID(), Instant.now().plusSeconds(900)))).isNull();
    }

    @Test
    void tokenMalformadoSegueAnonimo() throws Exception {
        assertThat(filtrar("nao-e-um-jwt")).isNull();
    }
}
