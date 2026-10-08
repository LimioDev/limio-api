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
                .orElseGet(() -> padraoContaNova(usuarioId));
    }

    /**
     * Padrões definidos no card: Push ligado, E-mail desligado. {@code sms} e
     * {@code whatsapp} ficam {@code null} (sem padrão) de propósito: o card não
     * define o do SMS e o do WhatsApp depende de um opt-in que o cadastro (UC01)
     * ainda não coleta — pendências 1 e 2 de {@code docs/features/UC15-...md}.
     */
    private PreferenciaNotificacao padraoContaNova(UUID usuarioId) {
        return PreferenciaNotificacao.builder()
                .usuarioId(usuarioId)
                .push(true)
                .email(false)
                .build();
    }
}
