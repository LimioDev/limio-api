package com.limio.api.modulos.notificacao.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacaoService;

/**
 * Regra de negócio da ETI-34 (atualizar preferências). Estado completo: os
 * quatro canais da preferência nova substituem os atuais, cada um de forma
 * independente. Cria o registro do usuário na primeira gravação; depois
 * atualiza sempre o mesmo (um por usuário). Não conhece HTTP nem record.
 */
@Service
public class AtualizarPreferenciaNotificacaoUseCase {

    private final PreferenciaNotificacaoService preferenciaNotificacaoService;

    public AtualizarPreferenciaNotificacaoUseCase(PreferenciaNotificacaoService preferenciaNotificacaoService) {
        this.preferenciaNotificacaoService = preferenciaNotificacaoService;
    }

    public PreferenciaNotificacao executar(UUID usuarioId, PreferenciaNotificacao preferenciaNova) {
        PreferenciaNotificacao preferencia = preferenciaNotificacaoService.buscarPorUsuario(usuarioId)
                .orElseGet(() -> PreferenciaNotificacao.builder().usuarioId(usuarioId).build());

        // Pendente (dependência externa): ligar o WhatsApp (false/sem registro -> true) deve gravar o
        // consentimento em AceiteTermo (documento, versao, origem = configuracoes, ip, aceitoEm). AceiteTermo
        // ainda não existe (reservado pra modulos.auth pelo TICKET-0044) e documento/versao/ip não têm
        // origem definida — ver "Dependências externas" em docs/features/UC15-...md.
        preferencia.setPush(preferenciaNova.getPush());
        preferencia.setEmail(preferenciaNova.getEmail());
        preferencia.setSms(preferenciaNova.getSms());
        preferencia.setWhatsapp(preferenciaNova.getWhatsapp());

        return preferenciaNotificacaoService.salvar(preferencia);
    }
}
