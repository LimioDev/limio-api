package com.limio.api.modulos.notificacao.actions.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.notificacao.actions.mapper.PreferenciaNotificacaoMapper;
import com.limio.api.modulos.notificacao.actions.usecase.AtualizarPreferenciaNotificacaoUseCase;
import com.limio.api.modulos.notificacao.actions.usecase.ConsultarPreferenciaNotificacaoUseCase;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.records.PreferenciaNotificacaoRequest;
import com.limio.api.modulos.notificacao.records.PreferenciaNotificacaoResponse;

import jakarta.validation.Valid;

/**
 * Preferências de notificação do usuário logado (ETI-34). O dono vem sempre do
 * token ({@link UsuarioAutenticado}), nunca do corpo nem da rota. Sem login, o
 * {@code SecurityConfig} responde 401 antes de chegar aqui.
 */
@RestController
@RequestMapping("/conta/preferencias-notificacao")
public class PreferenciaNotificacaoController {

    private final ConsultarPreferenciaNotificacaoUseCase consultarPreferenciaNotificacaoUseCase;
    private final AtualizarPreferenciaNotificacaoUseCase atualizarPreferenciaNotificacaoUseCase;
    private final PreferenciaNotificacaoMapper preferenciaNotificacaoMapper;

    public PreferenciaNotificacaoController(
            ConsultarPreferenciaNotificacaoUseCase consultarPreferenciaNotificacaoUseCase,
            AtualizarPreferenciaNotificacaoUseCase atualizarPreferenciaNotificacaoUseCase,
            PreferenciaNotificacaoMapper preferenciaNotificacaoMapper) {
        this.consultarPreferenciaNotificacaoUseCase = consultarPreferenciaNotificacaoUseCase;
        this.atualizarPreferenciaNotificacaoUseCase = atualizarPreferenciaNotificacaoUseCase;
        this.preferenciaNotificacaoMapper = preferenciaNotificacaoMapper;
    }

    @GetMapping
    public ResponseEntity<PreferenciaNotificacaoResponse> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        PreferenciaNotificacao preferencia = consultarPreferenciaNotificacaoUseCase.executar(usuario.id());
        return ResponseEntity.ok(preferenciaNotificacaoMapper.toResponse(preferencia));
    }

    @PutMapping
    public ResponseEntity<PreferenciaNotificacaoResponse> atualizar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody PreferenciaNotificacaoRequest request) {
        PreferenciaNotificacao preferenciaNova = preferenciaNotificacaoMapper.toEntity(request);
        PreferenciaNotificacao salva = atualizarPreferenciaNotificacaoUseCase.executar(usuario.id(), preferenciaNova);
        return ResponseEntity.ok(preferenciaNotificacaoMapper.toResponse(salva));
    }
}
