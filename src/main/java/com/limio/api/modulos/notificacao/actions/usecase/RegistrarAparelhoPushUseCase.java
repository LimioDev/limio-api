package com.limio.api.modulos.notificacao.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.notificacao.aparelhopush.AparelhoPushService;
import com.limio.api.modulos.notificacao.enums.Plataforma;

/**
 * Guarda o token de push do aparelho logado (ETI-112). Idempotente: registrar de novo o mesmo token não duplica. O
 * token é do aparelho, então se outra conta entrar nele o push passa a ir pra ela, e a anterior para de receber ali.
 */
@Service
public class RegistrarAparelhoPushUseCase {

    private final AparelhoPushService aparelhoPushService;

    public RegistrarAparelhoPushUseCase(AparelhoPushService aparelhoPushService) {
        this.aparelhoPushService = aparelhoPushService;
    }

    @Transactional
    public void executar(UUID usuarioId, String token, Plataforma plataforma) {
        aparelhoPushService.registrar(usuarioId, token, plataforma);
    }
}
