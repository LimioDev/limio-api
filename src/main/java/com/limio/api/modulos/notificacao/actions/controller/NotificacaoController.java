package com.limio.api.modulos.notificacao.actions.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.notificacao.actions.mapper.NotificacaoMapper;
import com.limio.api.modulos.notificacao.actions.usecase.ListarNotificacoesUseCase;
import com.limio.api.modulos.notificacao.actions.usecase.MarcarNotificacaoComoLidaUseCase;
import com.limio.api.modulos.notificacao.actions.usecase.MarcarTodasNotificacoesComoLidasUseCase;
import com.limio.api.modulos.notificacao.records.NaoLidasResponse;
import com.limio.api.modulos.notificacao.records.NotificacoesFiltro;
import com.limio.api.modulos.notificacao.records.NotificacoesResponse;

import jakarta.validation.Valid;

/**
 * Tela 43 "Notificações" e sino da tela 13 (ETI-110). O dono vem sempre do token ({@link UsuarioAutenticado}); sem
 * login, o {@code SecurityConfig} responde 401 antes de chegar aqui.
 */
@RestController
@RequestMapping("/notificacoes")
public class NotificacaoController {

    private final ListarNotificacoesUseCase listarNotificacoesUseCase;
    private final MarcarNotificacaoComoLidaUseCase marcarNotificacaoComoLidaUseCase;
    private final MarcarTodasNotificacoesComoLidasUseCase marcarTodasNotificacoesComoLidasUseCase;
    private final NotificacaoMapper notificacaoMapper;

    public NotificacaoController(ListarNotificacoesUseCase listarNotificacoesUseCase,
            MarcarNotificacaoComoLidaUseCase marcarNotificacaoComoLidaUseCase,
            MarcarTodasNotificacoesComoLidasUseCase marcarTodasNotificacoesComoLidasUseCase,
            NotificacaoMapper notificacaoMapper) {
        this.listarNotificacoesUseCase = listarNotificacoesUseCase;
        this.marcarNotificacaoComoLidaUseCase = marcarNotificacaoComoLidaUseCase;
        this.marcarTodasNotificacoesComoLidasUseCase = marcarTodasNotificacoesComoLidasUseCase;
        this.notificacaoMapper = notificacaoMapper;
    }

    @GetMapping
    public ResponseEntity<NotificacoesResponse> listar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @ModelAttribute NotificacoesFiltro filtro) {
        return ResponseEntity.ok(notificacaoMapper.toResponse(
                listarNotificacoesUseCase.executar(usuario.id(), filtro.pagina())));
    }

    @PostMapping("/{notificacaoId}/lida")
    public ResponseEntity<NaoLidasResponse> marcarComoLida(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID notificacaoId) {
        return ResponseEntity.ok(notificacaoMapper.naoLidas(
                marcarNotificacaoComoLidaUseCase.executar(notificacaoId, usuario.id())));
    }

    @PostMapping("/lidas")
    public ResponseEntity<NaoLidasResponse> marcarTodasComoLidas(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(notificacaoMapper.naoLidas(
                marcarTodasNotificacoesComoLidasUseCase.executar(usuario.id())));
    }
}
