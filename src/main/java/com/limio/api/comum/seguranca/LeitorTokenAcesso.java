package com.limio.api.comum.seguranca;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.ConsultaAdministrador;
import com.limio.api.comum.seguranca.enums.PapelUsuario;

/**
 * Confere o token de acesso e diz quem é o dono. Usado pelas rotas HTTP ({@link JwtAuthFilter}, {@link #lerDono}) e
 * pelo CONNECT do canal em tempo real ({@link WebSocketAuthInterceptor}, {@link #ler}).
 *
 * <p>Há dois donos possíveis: o usuário do app e o administrador do painel web (ETI-117). O token é decodificado uma
 * vez só e a claim {@link JwtAuthFilter#CLAIM_TIPO} decide qual dos dois ele é. Um nunca passa pelo outro: token de
 * administrador não vira usuário, e o canal em tempo real, que só usa {@link #ler}, recusa o administrador.
 *
 * <p>Todo token precisa ter {@code exp} (validador do {@code JwtConfig}). O de administrador também precisa apontar
 * para um administrador que ainda existe ({@link ConsultaAdministrador}): apagar a linha revoga o acesso na hora.
 */
@Component
public class LeitorTokenAcesso {

    private static final Logger log = LoggerFactory.getLogger(LeitorTokenAcesso.class);

    private final JwtDecoder jwtDecoder;
    private final ConsultaAdministrador consultaAdministrador;

    public LeitorTokenAcesso(JwtDecoder jwtDecoder, ConsultaAdministrador consultaAdministrador) {
        this.jwtDecoder = jwtDecoder;
        this.consultaAdministrador = consultaAdministrador;
    }

    /** Só usuário do app. Vazio se o token é inválido, expirado, sem {@code exp}, sem id e papel ou de administrador. */
    public Optional<TokenAcesso> ler(String token) {
        return decodificar(token, LeitorTokenAcesso::lerUsuario);
    }

    /**
     * Usuário do app ou administrador, numa decodificação só. Vazio se o token é inválido, expirado, sem {@code exp}
     * ou não tem um dono válido.
     */
    public Optional<DonoTokenAcesso> lerDono(String token) {
        return decodificar(token, jwt -> ehAdministrador(jwt)
                ? lerAdministrador(jwt).map(DonoTokenAcesso.class::cast)
                : lerUsuario(jwt).map(TokenAcesso::usuario));
    }

    private <T> Optional<T> decodificar(String token, Function<Jwt, Optional<T>> leitura) {
        try {
            return leitura.apply(jwtDecoder.decode(token));
        } catch (JwtValidationException e) {
            return Optional.empty(); // expirado, sem exp ou fora da validade: segue anônimo sem log
        } catch (JwtException | IllegalArgumentException e) {
            // assinatura inválida, token malformado ou claim inválida: segue anônimo, mas deixa rastro
            // pra distinguir tentativa de forjar token de tráfego normal. Nunca logar o token.
            log.debug("token de acesso recusado ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<TokenAcesso> lerUsuario(Jwt jwt) {
        String id = jwt.getSubject();
        String papel = jwt.getClaimAsString(JwtAuthFilter.CLAIM_PAPEL);
        if (id == null || papel == null || ehAdministrador(jwt)) {
            return Optional.empty();
        }
        UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.fromString(id), PapelUsuario.valueOf(papel));
        return Optional.of(new TokenAcesso(usuario, jwt.getExpiresAt()));
    }

    private Optional<AdministradorAutenticado> lerAdministrador(Jwt jwt) {
        String id = jwt.getSubject();
        if (id == null) {
            return Optional.empty();
        }
        UUID administradorId = UUID.fromString(id);
        if (!consultaAdministrador.existe(administradorId)) {
            return Optional.empty();
        }
        return Optional.of(new AdministradorAutenticado(administradorId));
    }

    private static boolean ehAdministrador(Jwt jwt) {
        return JwtAuthFilter.TIPO_ADMINISTRADOR.equals(jwt.getClaimAsString(JwtAuthFilter.CLAIM_TIPO));
    }
}
