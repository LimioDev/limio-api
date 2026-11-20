package com.limio.api.modulos.notificacao.actions.service;

import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.enums.CanalNotificacao;

/**
 * Um canal de envio dos avisos (ETI-112): uma implementação por {@link CanalNotificacao}. O
 * {@code EntregarNotificacaoUseCase} só confere a preferência e itera os canais; canal novo é uma implementação nova,
 * sem mexer no usecase.
 */
public interface CanalDeEnvioService {

    CanalNotificacao canal();

    /**
     * @return {@code true} se o provedor aceitou; {@code false} se não houve tentativa (sem aparelho, sem e-mail
     *     confirmado, sem consentimento, envio desligado), e nada é registrado
     * @throws RuntimeException quando o provedor recusou ou não respondeu: quem chama registra a falha
     */
    boolean enviar(Notificacao notificacao);
}
