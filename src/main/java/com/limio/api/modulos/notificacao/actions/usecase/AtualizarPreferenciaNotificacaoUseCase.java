package com.limio.api.modulos.notificacao.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.notificacao.excecao.WhatsappConsentimentoIndisponivelException;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacaoService;

/**
 * Regra de negócio da ETI-34 (atualizar preferências). Estado completo: os
 * quatro canais recebidos substituem os atuais, cada um de forma
 * independente. Upsert atômico (INSERT ... ON CONFLICT) por usuário:
 * cria na primeira gravação e atualiza o mesmo registro depois, sem corrida entre PUTs concorrentes. Não conhece HTTP nem record.
 */
@Service
public class AtualizarPreferenciaNotificacaoUseCase {

    private final PreferenciaNotificacaoService preferenciaNotificacaoService;

    public AtualizarPreferenciaNotificacaoUseCase(PreferenciaNotificacaoService preferenciaNotificacaoService) {
        this.preferenciaNotificacaoService = preferenciaNotificacaoService;
    }

    @Transactional
    public PreferenciaNotificacao executar(UUID usuarioId, boolean push, boolean email, boolean sms, boolean whatsapp) {
        // Dependência externa: ligar o WhatsApp deve gravar o consentimento em AceiteTermo (documento, versao,
        // origem = configuracoes, ip, aceitoEm). AceiteTermo ainda não existe (reservado pra modulos.auth pelo
        // TICKET-0044), então rejeita true até lá — ver "Dependências externas" em docs/features/UC15-...md.
        if (whatsapp) {
            throw new WhatsappConsentimentoIndisponivelException();
        }

        preferenciaNotificacaoService.gravarCanais(usuarioId, push, email, sms, whatsapp);

        return preferenciaNotificacaoService.buscarPorUsuario(usuarioId).orElseThrow();
    }
}
