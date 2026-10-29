package com.limio.api.comum.seguranca;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * A sessão do canal em tempo real vale até o token com que ela conectou vencer. Guarda a validade de cada sessão
 * (registrada pelo {@link WebSocketAuthInterceptor} no CONNECT) e, na saída, troca a primeira entrega depois do
 * vencimento por um frame ERROR: o Spring fecha a conexão logo depois de mandar um ERROR, e o app reconecta com o token
 * novo do refresh. O que não chegou fica no histórico do chat.
 *
 * Registrado no canal de saída ({@code clientOutboundChannel}).
 */
public class ExpiracaoSessaoWebSocket implements ChannelInterceptor {

    static final String MOTIVO = "token de acesso expirado";

    private final Map<String, Instant> expiraEmPorSessao = new ConcurrentHashMap<>();

    void registrar(String sessaoId, Instant expiraEm) {
        if (sessaoId != null && expiraEm != null)
            expiraEmPorSessao.put(sessaoId, expiraEm);
    }

    void remover(String sessaoId) {
        if (sessaoId != null)
            expiraEmPorSessao.remove(sessaoId);
    }

    boolean expirou(String sessaoId) {
        Instant expiraEm = sessaoId == null ? null : expiraEmPorSessao.get(sessaoId);
        return expiraEm != null && !Instant.now().isBefore(expiraEm);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE)
            return message;
        String sessaoId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        if (!expirou(sessaoId))
            return message;
        remover(sessaoId);
        return erro(sessaoId);
    }

    private static Message<byte[]> erro(String sessaoId) {
        StompHeaderAccessor erro = StompHeaderAccessor.create(StompCommand.ERROR);
        erro.setMessage(MOTIVO);
        erro.setSessionId(sessaoId);
        erro.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], erro.getMessageHeaders());
    }
}
