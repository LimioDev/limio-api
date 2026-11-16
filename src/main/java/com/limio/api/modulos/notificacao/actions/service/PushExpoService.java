package com.limio.api.modulos.notificacao.actions.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Push pelo Expo Push Service (decisão de 2026-11-16, ETI-112): o app usa {@code expo-notifications} e registra o
 * {@code ExponentPushToken}, que serve Android e iOS sem configurar Firebase nem APNs aqui. Até 100 mensagens por
 * chamada (limite do Expo).
 *
 * <p>Com {@code notificacao.push.enviar=false} (padrão fora de produção) só loga, igual ao e-mail: dev local e testes
 * não dependem de rede. Falha de rede ou resposta de erro do Expo sobe como exceção pra quem chama registrar a falha.
 */
@Component
public class PushExpoService {

    private static final Logger log = LoggerFactory.getLogger(PushExpoService.class);
    private static final int LOTE_MAXIMO = 100;
    private static final String TOKEN_INVALIDO = "DeviceNotRegistered";

    private final RestClient restClient;
    private final boolean envioHabilitado;

    public PushExpoService(@Value("${notificacao.push.expo.url}") String url,
            @Value("${notificacao.push.expo.access-token:}") String accessToken,
            @Value("${notificacao.push.enviar}") boolean envioHabilitado) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "limio-api");
        if (!accessToken.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        this.restClient = builder.build();
        this.envioHabilitado = envioHabilitado;
    }

    /**
     * @param dados vão no {@code data} da mensagem: o app usa pra abrir a tela certa ao tocar
     * @return os tokens que o Expo disse que não existem mais (app desinstalado, permissão revogada), pra apagar
     */
    public List<String> enviar(List<String> tokens, String titulo, String corpo, Map<String, String> dados) {
        if (!envioHabilitado) {
            log.info("push não enviado (notificacao.push.enviar=false): aparelhos={}, titulo={}", tokens.size(),
                    titulo);
            return List.of();
        }
        List<String> invalidos = new ArrayList<>();
        for (int inicio = 0; inicio < tokens.size(); inicio += LOTE_MAXIMO) {
            List<String> lote = tokens.subList(inicio, Math.min(inicio + LOTE_MAXIMO, tokens.size()));
            invalidos.addAll(enviarLote(lote, titulo, corpo, dados));
        }
        return invalidos;
    }

    private List<String> enviarLote(List<String> lote, String titulo, String corpo, Map<String, String> dados) {
        List<MensagemExpo> mensagens = lote.stream()
                .map(token -> new MensagemExpo(token, titulo, corpo, dados, "default"))
                .toList();
        RespostaExpo resposta = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(mensagens)
                .retrieve()
                .body(RespostaExpo.class);
        if (resposta == null || resposta.data() == null) {
            throw new IllegalStateException("Expo respondeu sem tickets");
        }
        List<String> invalidos = new ArrayList<>();
        for (int i = 0; i < resposta.data().size() && i < lote.size(); i++) {
            TicketExpo ticket = resposta.data().get(i);
            if ("error".equals(ticket.status())) {
                String erro = ticket.details() == null ? null : ticket.details().error();
                if (TOKEN_INVALIDO.equals(erro)) {
                    invalidos.add(lote.get(i));
                } else {
                    log.warn("Expo recusou um push: {} ({})", ticket.message(), erro);
                }
            }
        }
        return invalidos;
    }

    private record MensagemExpo(String to, String title, String body, Map<String, String> data, String sound) {
    }

    private record RespostaExpo(List<TicketExpo> data) {
    }

    private record TicketExpo(String status, String message, DetalheExpo details) {
    }

    private record DetalheExpo(String error) {
    }
}
