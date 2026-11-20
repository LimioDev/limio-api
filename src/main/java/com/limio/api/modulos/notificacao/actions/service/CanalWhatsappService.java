package com.limio.api.modulos.notificacao.actions.service;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.enums.CanalNotificacao;

/**
 * WhatsApp (ETI-112): <b>nunca envia</b>, mesmo com o canal ligado na preferência. Decisão do PO: só sai com
 * consentimento ativo ({@code AceiteTermo} gravado e {@code whatsapp = true}). {@code AceiteTermo} ainda não existe,
 * então ninguém tem consentimento; também não há provedor escolhido. Quando os dois existirem, o envio entra aqui.
 */
@Component
public class CanalWhatsappService implements CanalDeEnvioService {

    @Override
    public CanalNotificacao canal() {
        return CanalNotificacao.WHATSAPP;
    }

    /** Sempre {@code false}: sem consentimento não há tentativa, e nada é registrado. */
    @Override
    public boolean enviar(Notificacao notificacao) {
        return false;
    }
}
