package com.limio.api.modulos.notificacao.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacaoService;

/**
 * Regra de negócio da ETI-34 (consultar preferências). Devolve a preferência
 * salva do usuário ou, se ele nunca salvou, os padrões de conta nova — sem
 * persistir nada. Não conhece HTTP nem record.
 */
@Service
public class ConsultarPreferenciaNotificacaoUseCase {

    private final PreferenciaNotificacaoService preferenciaNotificacaoService;

    public ConsultarPreferenciaNotificacaoUseCase(PreferenciaNotificacaoService preferenciaNotificacaoService) {
        this.preferenciaNotificacaoService = preferenciaNotificacaoService;
    }

    public PreferenciaNotificacao executar(UUID usuarioId) {
        return preferenciaNotificacaoService.buscarPorUsuario(usuarioId)
                .orElseGet(() -> PreferenciaNotificacao.padraoContaNova(usuarioId));
    }
}
