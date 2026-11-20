package com.limio.api.modulos.notificacao.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.notificacao.aparelhopush.AparelhoPushService;

/**
 * Remove o aparelho do push no logout (ETI-112): o app chama antes de sair da conta. Só apaga o token se ele for do
 * usuário logado; token de outra conta ou já removido não é erro (o logout não pode travar por isso).
 */
@Service
public class RemoverAparelhoPushUseCase {

    private final AparelhoPushService aparelhoPushService;

    public RemoverAparelhoPushUseCase(AparelhoPushService aparelhoPushService) {
        this.aparelhoPushService = aparelhoPushService;
    }

    @Transactional
    public void executar(UUID usuarioId, String token) {
        aparelhoPushService.remover(usuarioId, token);
    }
}
