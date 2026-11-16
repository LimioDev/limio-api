package com.limio.api.modulos.notificacao.actions.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.notificacao.actions.usecase.RegistrarAparelhoPushUseCase;
import com.limio.api.modulos.notificacao.actions.usecase.RemoverAparelhoPushUseCase;
import com.limio.api.modulos.notificacao.records.AparelhoPushRequest;
import com.limio.api.modulos.notificacao.records.RemoverAparelhoPushRequest;

import jakarta.validation.Valid;

/**
 * Aparelho do usuário logado pra push (ETI-112, card par [FRONT-END]): registrar ao entrar e remover ao sair. Remover
 * é {@code POST} com corpo porque o token tem colchetes ({@code ExponentPushToken[...]}) e alguns clientes descartam o
 * corpo de {@code DELETE}.
 */
@RestController
@RequestMapping("/notificacoes/aparelhos")
public class AparelhoPushController {

    private final RegistrarAparelhoPushUseCase registrarAparelhoPushUseCase;
    private final RemoverAparelhoPushUseCase removerAparelhoPushUseCase;

    public AparelhoPushController(RegistrarAparelhoPushUseCase registrarAparelhoPushUseCase,
            RemoverAparelhoPushUseCase removerAparelhoPushUseCase) {
        this.registrarAparelhoPushUseCase = registrarAparelhoPushUseCase;
        this.removerAparelhoPushUseCase = removerAparelhoPushUseCase;
    }

    @PostMapping
    public ResponseEntity<Void> registrar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody AparelhoPushRequest request) {
        registrarAparelhoPushUseCase.executar(usuario.id(), request.token(), request.plataforma());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/remover")
    public ResponseEntity<Void> remover(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody RemoverAparelhoPushRequest request) {
        removerAparelhoPushUseCase.executar(usuario.id(), request.token());
        return ResponseEntity.noContent().build();
    }
}
