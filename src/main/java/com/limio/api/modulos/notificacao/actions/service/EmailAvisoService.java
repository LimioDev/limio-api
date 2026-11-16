package com.limio.api.modulos.notificacao.actions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;

/**
 * E-mail dos avisos via Resend (ETI-45/TICKET-0033): mesmo provedor e mesma chave de {@code auth} e {@code
 * financeiro}, instância própria porque {@code notificacao} não importa classe de outro módulo (ADR-0001). Nome
 * específico pra não colidir com o {@code EmailService} de {@code auth}.
 *
 * <p>Diferente do {@code auth}, falha sobe como exceção: quem chama registra o envio como falho. Com
 * {@code email.enviar=false} (padrão fora de produção) só loga.
 */
@Component
public class EmailAvisoService {

    private static final Logger log = LoggerFactory.getLogger(EmailAvisoService.class);

    private final Resend resend;
    private final String remetente;
    private final boolean envioHabilitado;

    public EmailAvisoService(@Value("${email.resend.api-key}") String apiKey,
            @Value("${email.resend.remetente}") String remetente,
            @Value("${email.enviar}") boolean envioHabilitado) {
        this.resend = new Resend(apiKey);
        this.remetente = remetente;
        this.envioHabilitado = envioHabilitado;
    }

    /** O texto vem de dado do usuário (título do anúncio, nome): escapado antes de entrar no HTML. */
    public void enviar(String destinatario, String titulo, String corpo) {
        if (!envioHabilitado) {
            log.info("e-mail não enviado (email.enviar=false): destinatario={}, assunto={}", destinatario, titulo);
            return;
        }
        try {
            resend.emails().send(CreateEmailOptions.builder()
                    .from(remetente)
                    .to(destinatario)
                    .subject(titulo)
                    .html("<p>" + HtmlUtils.htmlEscape(corpo) + "</p>"
                            + "<p>Abra o app Limio para ver os detalhes.</p>")
                    .build());
        } catch (ResendException e) {
            throw new IllegalStateException("Resend recusou o e-mail: " + e.getMessage(), e);
        }
    }
}
