package com.limio.api.comum.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
    /** Administradores que existem na base, como a porta {@code ConsultaAdministrador} responderia. */
    private final Set<UUID> administradores = new HashSet<>();
    private final LeitorTokenAcesso leitor = new LeitorTokenAcesso(jwtConfig.jwtDecoder(), administradores::contains);
    private final JwtAuthFilter filtro = new JwtAuthFilter(leitor);

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
        assertThat(autenticacao.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_" + JwtAuthFilter.ROLE_USUARIO);
    }

    @Test
    void tokenDeAdministradorViraAdministradorAutenticado() throws Exception {
        UUID id = UUID.randomUUID();
        administradores.add(id);

        Authentication autenticacao = filtrar(TokensDeTeste.administrador(jwtConfig.jwtEncoder(), id));

        assertThat(autenticacao.getPrincipal()).isEqualTo(new AdministradorAutenticado(id));
        assertThat(autenticacao.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_" + JwtAuthFilter.ROLE_ADMINISTRADOR);
    }

    @Test
    void tokenDeAdministradorNaoEhLidoComoUsuario() {
        UUID id = UUID.randomUUID();
        administradores.add(id);
        String token = TokensDeTeste.administrador(jwtConfig.jwtEncoder(), id);

        assertThat(leitor.ler(token)).as("rotas do app e canal em tempo real").isEmpty();
    }

    @Test
    void tokenDeUsuarioNaoEhLidoComoAdministrador() {
        UUID id = UUID.randomUUID();
        administradores.add(id);
        String token = token(jwtConfig.jwtEncoder(), id, Instant.now().plusSeconds(900));

        assertThat(leitor.lerDono(token)).containsInstanceOf(UsuarioAutenticado.class);
    }

    @Test
    void tokenDeAdministradorQueNaoExisteMaisSegueAnonimo() throws Exception {
        String token = TokensDeTeste.administrador(jwtConfig.jwtEncoder(), UUID.randomUUID());

        assertThat(filtrar(token)).as("administrador removido ou id qualquer").isNull();
    }

    @Test
    void tokenSemExpiracaoSegueAnonimo() throws Exception {
        UUID administradorId = UUID.randomUUID();
        administradores.add(administradorId);
        String administradorSemExp = semExpiracao(JwtClaimsSet.builder().subject(administradorId.toString())
                .claim(JwtAuthFilter.CLAIM_TIPO, JwtAuthFilter.TIPO_ADMINISTRADOR));
        String usuarioSemExp = semExpiracao(JwtClaimsSet.builder().subject(UUID.randomUUID().toString())
                .claim(JwtAuthFilter.CLAIM_PAPEL, "PRESTADOR"));

        assertThat(filtrar(administradorSemExp)).isNull();
        assertThat(filtrar(usuarioSemExp)).isNull();
        assertThat(leitor.ler(usuarioSemExp)).as("canal em tempo real").isEmpty();
    }

    private String semExpiracao(JwtClaimsSet.Builder claims) {
        return jwtConfig.jwtEncoder().encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.issuedAt(Instant.now()).build())).getTokenValue();
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
