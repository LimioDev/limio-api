package com.limio.api.modulos.auth.actions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;

/**
 * Envio de e-mail transacional via Resend (provedor decidido pelo tech lead
 * no TICKET-0033). Falha de envio só é logada, nunca propagada: a
 * solicitação de recuperação sempre responde igual (anti-enumeração) e a
 * notificação de senha alterada é um aviso, não a garantia da troca em si.
 *
 * Com {@code email.enviar=false} (padrão fora de produção) só loga em vez de
 * chamar a API — dev local e os testes não dependem de rede nem de uma API
 * key real.
 */
@Component
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

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

    public void enviarRecuperacaoSenha(String destinatario, String link) {
        enviar(destinatario, "Redefinir sua senha",
                "<p>Recebemos um pedido para redefinir sua senha.</p>"
                        + "<p><a href=\"" + link + "\">Clique aqui para criar uma nova senha</a></p>"
                        + "<p>O link vale por 1 hora. Se não foi você, ignore este e-mail.</p>");
    }

    public void enviarSenhaAlterada(String destinatario, String linkRecuperacao) {
        enviar(destinatario, "Sua senha foi alterada",
                "<p>Sua senha foi alterada agora.</p>"
                        + "<p>Não foi você? <a href=\"" + linkRecuperacao + "\">Recupere o acesso à sua conta</a></p>");
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
