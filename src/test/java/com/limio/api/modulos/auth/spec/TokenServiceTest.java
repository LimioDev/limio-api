package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.config.JwtConfig;
import com.limio.api.comum.seguranca.JwtAuthFilter;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.actions.helper.RefreshTokenHelper;
import com.limio.api.modulos.auth.actions.service.TokenService;
import com.limio.api.modulos.auth.sessao.Sessao;
import com.limio.api.modulos.auth.sessao.TokensSessao;
import com.limio.api.modulos.auth.usuario.Usuario;

class TokenServiceTest {

    private final JwtConfig jwtConfig = new JwtConfig("segredo-de-teste-com-pelo-menos-32-bytes");
    private final TokenService tokenService =
            new TokenService(jwtConfig.jwtEncoder(), Duration.ofMinutes(15), Duration.ofDays(30));

    private Sessao sessaoDePrestador() {
        Usuario usuario = Usuario.builder().papelAtivo(PapelUsuario.PRESTADOR).build();
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        return Sessao.builder().usuario(usuario).build();
    }

    @Test
    void tokenDeAcessoCarregaIdEPapelAtivoNoFormatoQueOFiltroLe() {
        Sessao sessao = sessaoDePrestador();
        Instant agora = Instant.now();

        TokensSessao tokens = tokenService.emitir(sessao, agora);

        Jwt jwt = jwtConfig.jwtDecoder().decode(tokens.tokenAcesso());
        assertThat(jwt.getSubject()).isEqualTo(sessao.getUsuario().getId().toString());
        assertThat(jwt.getClaimAsString(JwtAuthFilter.CLAIM_PAPEL)).isEqualTo("PRESTADOR");
        assertThat(jwt.getExpiresAt()).isEqualTo(tokens.tokenAcessoExpiraEm());
        assertThat(tokens.tokenAcessoExpiraEm())
                .isBetween(agora.plus(Duration.ofMinutes(15)).minusSeconds(1), agora.plus(Duration.ofMinutes(15)));
        assertThat(tokens.papel()).isEqualTo(PapelUsuario.PRESTADOR);
    }

    @Test
    void sessaoGuardaSoOHashDoRefreshTokenEGanhaValidadeNova() {
        Sessao sessao = sessaoDePrestador();
        Instant agora = Instant.now();

        TokensSessao tokens = tokenService.emitir(sessao, agora);

        assertThat(sessao.getRefreshTokenHash())
                .isEqualTo(RefreshTokenHelper.hash(tokens.refreshToken()))
                .isNotEqualTo(tokens.refreshToken());
        assertThat(sessao.getUltimoUsoEm()).isEqualTo(agora);
        assertThat(sessao.getExpiraEm()).isEqualTo(agora.plus(Duration.ofDays(30)));
    }
}
