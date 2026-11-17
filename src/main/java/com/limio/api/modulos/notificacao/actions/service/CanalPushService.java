package com.limio.api.modulos.notificacao.actions.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.actions.service.PushExpoService.ResultadoPush;
import com.limio.api.modulos.notificacao.aparelhopush.AparelhoPushService;
import com.limio.api.modulos.notificacao.enums.CanalNotificacao;

/**
 * Push pros aparelhos registrados do usuário (ETI-112). Sem aparelho, nada a enviar. Token que o Expo diz não existir
 * mais é apagado, mesmo quando outro lote falhou; a falha de um lote vira exceção depois disso.
 */
@Component
public class CanalPushService implements CanalDeEnvioService {

    private final AparelhoPushService aparelhoPushService;
    private final PushExpoService pushExpoService;

    public CanalPushService(AparelhoPushService aparelhoPushService, PushExpoService pushExpoService) {
        this.aparelhoPushService = aparelhoPushService;
        this.pushExpoService = pushExpoService;
    }

    @Override
    public CanalNotificacao canal() {
        return CanalNotificacao.PUSH;
    }

    @Override
    public boolean enviar(Notificacao notificacao) {
        List<String> tokens = aparelhoPushService.tokensDoUsuario(notificacao.getUsuarioId());
        if (tokens.isEmpty()) {
            return false;
        }
        ResultadoPush resultado = pushExpoService.enviar(tokens, notificacao.getTitulo(), notificacao.getCorpo(),
                dadosDoToque(notificacao));
        aparelhoPushService.apagarTokens(resultado.tokensInvalidos());
        resultado.falha().ifPresent(falha -> {
            throw new IllegalStateException(falha);
        });
        return resultado.tentou();
    }

    /** O que o app precisa pra abrir a tela certa ao tocar no push (card par [FRONT-END]). */
    private static Map<String, String> dadosDoToque(Notificacao notificacao) {
        Map<String, String> dados = new HashMap<>();
        dados.put("notificacaoId", notificacao.getId().toString());
        dados.put("tipo", notificacao.getTipo().name());
        if (notificacao.getAnuncioId() != null) {
            dados.put("anuncioId", notificacao.getAnuncioId().toString());
        }
        return dados;
    }
}
