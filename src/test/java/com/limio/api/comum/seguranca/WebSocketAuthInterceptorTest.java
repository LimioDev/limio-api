package com.limio.api.comum.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

class WebSocketAuthInterceptorTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final MessageChannel canal = mock(MessageChannel.class);
    private final WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(jwtDecoder);

    private static Message<byte[]> frame(StompHeaderAccessor stomp) {
        stomp.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], stomp.getMessageHeaders());
    }

    private static Message<byte[]> connect(String authorization) {
        StompHeaderAccessor stomp = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null)
            stomp.addNativeHeader(HttpHeaders.AUTHORIZATION, authorization);
        return frame(stomp);
    }

    private static Message<byte[]> subscribe(String destino, boolean autenticado) {
        StompHeaderAccessor stomp = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        stomp.setDestination(destino);
        if (autenticado)
            stomp.setUser(UsernamePasswordAuthenticationToken.authenticated(
                    new UsuarioAutenticado(USUARIO_ID, PapelUsuario.PRESTADOR), null, List.of()));
        return frame(stomp);
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject(USUARIO_ID.toString())
                .claim(JwtAuthFilter.CLAIM_PAPEL, "PRESTADOR").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    void dadoTokenValido_quandoConecta_entaoUsuarioIdentificadoPeloId() {
        when(jwtDecoder.decode("valido")).thenReturn(jwt());

        Message<?> mensagem = interceptor.preSend(connect("Bearer valido"), canal);

        StompHeaderAccessor stomp = StompHeaderAccessor.wrap(mensagem);
        assertThat(stomp.getUser()).isNotNull();
        assertThat(stomp.getUser().getName()).isEqualTo(USUARIO_ID.toString());
    }

    @Test
    void dadoTokenAusenteOuInvalido_quandoConecta_entaoRecusa() {
        when(jwtDecoder.decode("ruim")).thenThrow(new BadJwtException("assinatura"));

        assertThatThrownBy(() -> interceptor.preSend(connect(null), canal))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer ruim"), canal))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void dadaFilaDeOutroDestino_quandoAssina_entaoRecusa() {
        assertThat(interceptor.preSend(subscribe("/user/queue/chat", true), canal)).isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/queue/chat", true), canal))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/user/queue/chat", false), canal))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void dadoFrameSend_quandoChega_entaoRecusa() {
        StompHeaderAccessor stomp = StompHeaderAccessor.create(StompCommand.SEND);
        stomp.setDestination("/app/qualquer");

        assertThatThrownBy(() -> interceptor.preSend(frame(stomp), canal))
                .isInstanceOf(MessageDeliveryException.class);
    }
}
