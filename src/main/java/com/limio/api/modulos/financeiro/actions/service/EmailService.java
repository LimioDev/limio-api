package com.limio.api.modulos.financeiro.actions.service;

import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;

/**
 * Envio do recibo da assinatura GOLD via Resend — mesmo provedor e mesma chave de {@code auth}
 * (TICKET-0033), mas instância própria: {@code financeiro} não importa classe de outro módulo
 * (ADR-0001). Com {@code email.enviar=false} (padrão fora de produção) só loga em vez de chamar a API.
 */
@Component("financeiroEmailService")
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Resend resend;
    private final String remetente;
    private final boolean envioHabilitado;

    public EmailService(@Value("${email.resend.api-key}") String apiKey,
            @Value("${email.resend.remetente}") String remetente,
            @Value("${email.enviar}") boolean envioHabilitado) {
        this.resend = new Resend(apiKey);
        this.remetente = remetente;
        this.envioHabilitado = envioHabilitado;
    }

    public void enviarReciboGold(String destinatario, String nomePlano, long valorCentavos, String moeda,
            ZonedDateTime renovaEm, String numeroRecibo) {
        NumberFormat moedaFormatada = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        moedaFormatada.setCurrency(java.util.Currency.getInstance(moeda));
        String valor = moedaFormatada.format(valorCentavos / 100.0);
        enviar(destinatario, "Recibo da assinatura " + nomePlano,
                "<p>Recebemos seu pagamento de " + valor + " do " + nomePlano + ".</p>"
                        + "<p>Recibo: " + numeroRecibo + "</p>"
                        + "<p>Sua assinatura renova em " + renovaEm.format(FORMATO_DATA) + ".</p>");
    }

    private void enviar(String destinatario, String assunto, String corpoHtml) {
        if (!envioHabilitado) {
            log.info("e-mail não enviado (email.enviar=false): destinatario={}, assunto={}", destinatario, assunto);
            return;
        }
        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(remetente)
                    .to(destinatario)
                    .subject(assunto)
                    .html(corpoHtml)
                    .build();
            resend.emails().send(params);
        } catch (ResendException e) {
            log.error("falha ao enviar e-mail via Resend pra {}", destinatario, e);
        }
    }
}
