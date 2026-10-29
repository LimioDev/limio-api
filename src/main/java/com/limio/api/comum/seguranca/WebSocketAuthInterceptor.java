package com.limio.api.comum.seguranca;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Autenticação do canal em tempo real (STOMP sobre {@code /ws}). O handshake HTTP é público porque o app nativo não
 * manda header nele; o token de acesso vai no header {@code Authorization} do frame CONNECT e é lido igual ao
 * {@link JwtAuthFilter}. Sem token válido a conexão é recusada.
 *
 * O canal só entrega: o cliente assina as próprias filas ({@code /user/queue/...}) e não envia nada por aqui. Envio,
 * entrega e leitura passam pelas rotas HTTP.
 */
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String PREFIXO_BEARER = "Bearer ";
    private static final String PREFIXO_FILAS_DO_USUARIO = "/user/queue/";

    private final JwtDecoder jwtDecoder;

    public WebSocketAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message;
        }
        switch (stomp.getCommand()) {
            case CONNECT, STOMP -> stomp.setUser(autenticar(stomp));
            case SUBSCRIBE -> exigirFilaPropria(stomp);
            case SEND -> throw new MessageDeliveryException("o canal em tempo real não recebe mensagens");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK, ...: nada a checar
            }
        }
        return message;
    }

    private UsernamePasswordAuthenticationToken autenticar(StompHeaderAccessor stomp) {
        String cabecalho = stomp.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        UsuarioAutenticado usuario = cabecalho != null && cabecalho.startsWith(PREFIXO_BEARER)
                ? JwtAuthFilter.lerToken(jwtDecoder, cabecalho.substring(PREFIXO_BEARER.length()))
                : null;
        if (usuario == null) {
            throw new MessageDeliveryException("token de acesso ausente ou inválido");
        }
        return UsernamePasswordAuthenticationToken.authenticated(usuario, null, List.of());
    }

    private static void exigirFilaPropria(StompHeaderAccessor stomp) {
        String destino = stomp.getDestination();
        if (stomp.getUser() == null || destino == null || !destino.startsWith(PREFIXO_FILAS_DO_USUARIO)) {
            throw new MessageDeliveryException("só é possível assinar as próprias filas");
        }
    }
}
