package com.limio.api.modulos.notificacao.actions.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * <p>Com {@code notificacao.push.enviar=false} (padrão fora de produção) só loga e devolve
 * {@link ResultadoPush#naoTentou()}, igual ao e-mail: dev local e testes não dependem de rede, e nada é registrado como
 * enviado. Cada lote é tratado à parte: um lote que falha não descarta os tokens inválidos que os outros lotes já
 * devolveram; a falha volta no resultado pra quem chama registrar.
 */
@Component
public class PushExpoService {

    private static final Logger log = LoggerFactory.getLogger(PushExpoService.class);
    private static final int LOTE_MAXIMO = 100;
    private static final String TOKEN_INVALIDO = "DeviceNotRegistered";

    private final RestClient restClient;
    private final boolean envioHabilitado;

    @Autowired
    public PushExpoService(@Value("${notificacao.push.expo.url}") String url,
            @Value("${notificacao.push.expo.access-token:}") String accessToken,
            @Value("${notificacao.push.enviar}") boolean envioHabilitado) {
        this(RestClient.builder(), url, accessToken, envioHabilitado);
    }

    /** Recebe o {@code builder} pra o spec ligar um {@code MockRestServiceServer} nele. */
    public PushExpoService(RestClient.Builder builder, String url, String accessToken, boolean envioHabilitado) {
        builder.baseUrl(url)
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
     */
    public ResultadoPush enviar(List<String> tokens, String titulo, String corpo, Map<String, String> dados) {
        if (!envioHabilitado) {
            log.info("push não enviado (notificacao.push.enviar=false): aparelhos={}, titulo={}", tokens.size(),
                    titulo);
            return ResultadoPush.naoTentou();
        }
        int lotes = (tokens.size() + LOTE_MAXIMO - 1) / LOTE_MAXIMO;
        List<String> invalidos = new ArrayList<>();
        List<String> falhas = new ArrayList<>();
        for (int lote = 0; lote < lotes; lote++) {
            int inicio = lote * LOTE_MAXIMO;
            List<String> tokensDoLote = tokens.subList(inicio, Math.min(inicio + LOTE_MAXIMO, tokens.size()));
            try {
                invalidos.addAll(enviarLote(tokensDoLote, titulo, corpo, dados));
            } catch (RuntimeException e) {
                log.warn("lote {} de {} do push falhou", lote + 1, lotes, e);
                falhas.add("lote " + (lote + 1) + " de " + lotes + ": " + e.getClass().getSimpleName() + ": "
                        + e.getMessage());
            }
        }
        return new ResultadoPush(true, List.copyOf(invalidos),
                falhas.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", falhas)));
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

    /**
     * @param tentou {@code false} quando o envio está desligado e nada saiu
     * @param tokensInvalidos os que o Expo disse que não existem mais (app desinstalado, permissão revogada), de todos
     *     os lotes que responderam, pra apagar
     * @param falha os lotes que falharam (rede, erro do Expo); vazio quando todos foram aceitos
     */
    public record ResultadoPush(boolean tentou, List<String> tokensInvalidos, Optional<String> falha) {

        public static ResultadoPush naoTentou() {
            return new ResultadoPush(false, List.of(), Optional.empty());
        }

        public static ResultadoPush enviado(List<String> tokensInvalidos) {
            return new ResultadoPush(true, tokensInvalidos, Optional.empty());
        }
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
