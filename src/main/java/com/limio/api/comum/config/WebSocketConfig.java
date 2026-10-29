package com.limio.api.comum.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.limio.api.comum.seguranca.WebSocketAuthInterceptor;

/**
 * Canal em tempo real (ETI-101): STOMP sobre WebSocket em {@code /ws}, com broker em memória. Cada usuário assina
 * {@code /user/queue/...} e o servidor entrega com {@code convertAndSendToUser(id, ...)}. Broker em memória serve
 * enquanto a API roda numa instância só; com mais de uma, troca por um relay (RabbitMQ/Redis).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Handshake público: a autenticação acontece no frame CONNECT ({@link WebSocketAuthInterceptor}). */
    public static final String ROTA_WEBSOCKET = "/ws";

    private final JwtDecoder jwtDecoder;
    private final List<String> origensPermitidas;

    public WebSocketConfig(JwtDecoder jwtDecoder, @Value("${cors.origens-permitidas}") List<String> origensPermitidas) {
        this.jwtDecoder = jwtDecoder;
        this.origensPermitidas = origensPermitidas.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // mesmas origens do CorsConfig; o app nativo não manda Origin e passa direto
        registry.addEndpoint(ROTA_WEBSOCKET).setAllowedOriginPatterns(origensPermitidas.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new WebSocketAuthInterceptor(jwtDecoder));
    }
}
