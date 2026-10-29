package com.limio.api.comum.seguranca;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * Autenticação do canal em tempo real (STOMP sobre {@code /ws}). O handshake HTTP é público porque o app nativo não
 * manda header nele; o token de acesso vai no header {@code Authorization} do frame CONNECT e é lido pelo
 * {@link LeitorTokenAcesso}, como nas rotas HTTP. Sem token válido a conexão é recusada.
 *
 * A sessão vale até o token vencer ({@link ExpiracaoSessaoWebSocket}): depois disso o SUBSCRIBE é recusado e a próxima
 * entrega fecha a conexão.
 *
 * O canal só entrega: o cliente assina as próprias filas ({@code /user/queue/...}) e não envia nada por aqui. Envio,
 * entrega e leitura passam pelas rotas HTTP.
 */
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String PREFIXO_BEARER = "Bearer ";
    private static final String PREFIXO_FILAS_DO_USUARIO = "/user/queue/";

    private final LeitorTokenAcesso leitorTokenAcesso;
    private final ExpiracaoSessaoWebSocket expiracao;

    public WebSocketAuthInterceptor(LeitorTokenAcesso leitorTokenAcesso, ExpiracaoSessaoWebSocket expiracao) {
        this.leitorTokenAcesso = leitorTokenAcesso;
        this.expiracao = expiracao;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message;
        }
        switch (stomp.getCommand()) {
            case CONNECT, STOMP -> autenticar(stomp);
            case SUBSCRIBE -> exigirFilaPropriaNaValidade(stomp);
            case SEND -> throw new MessageDeliveryException("o canal em tempo real não recebe mensagens");
            case DISCONNECT -> expiracao.remover(stomp.getSessionId());
            default -> {
                // UNSUBSCRIBE, ACK, ...: nada a checar
            }
        }
        return message;
    }

    private void autenticar(StompHeaderAccessor stomp) {
        String cabecalho = stomp.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (cabecalho == null || !cabecalho.startsWith(PREFIXO_BEARER)) {
            throw new MessageDeliveryException("token de acesso ausente");
        }
        TokenAcesso acesso = leitorTokenAcesso.ler(cabecalho.substring(PREFIXO_BEARER.length()))
                .orElseThrow(() -> new MessageDeliveryException("token de acesso inválido"));
        stomp.setUser(UsernamePasswordAuthenticationToken.authenticated(acesso.usuario(), null, List.of()));
        expiracao.registrar(stomp.getSessionId(), acesso.expiraEm());
    }

    private void exigirFilaPropriaNaValidade(StompHeaderAccessor stomp) {
        String destino = stomp.getDestination();
        if (stomp.getUser() == null || destino == null || !destino.startsWith(PREFIXO_FILAS_DO_USUARIO)) {
            throw new MessageDeliveryException("só é possível assinar as próprias filas");
        }
        if (expiracao.expirou(stomp.getSessionId())) {
            throw new MessageDeliveryException(ExpiracaoSessaoWebSocket.MOTIVO);
        }
    }
}
