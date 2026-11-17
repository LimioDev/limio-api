package com.limio.api.modulos.notificacao.actions.service;

import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.enums.CanalNotificacao;

/** E-mail pelo Resend (ETI-112), só pra e-mail confirmado. */
@Component
public class CanalEmailService implements CanalDeEnvioService {

    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final EmailAvisoService emailAvisoService;

    public CanalEmailService(ConsultaDadosUsuario consultaDadosUsuario, EmailAvisoService emailAvisoService) {
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.emailAvisoService = emailAvisoService;
    }

    @Override
    public CanalNotificacao canal() {
        return CanalNotificacao.EMAIL;
    }

    @Override
    public boolean enviar(Notificacao notificacao) {
        return consultaDadosUsuario.buscarPorId(notificacao.getUsuarioId())
                .filter(DadosUsuario::emailConfirmado)
                .map(DadosUsuario::email)
                .map(destinatario -> emailAvisoService.enviar(destinatario, notificacao.getTitulo(),
                        notificacao.getCorpo()))
                .orElse(false);
    }
}
