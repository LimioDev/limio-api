package com.limio.api.modulos.notificacao.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.records.PreferenciaNotificacaoResponse;

/**
 * Anti-corruption layer entre {@link PreferenciaNotificacao} e o record de resposta HTTP. Chamado só pelo
 * controller. Não implementa {@code BaseMapper}: não há {@code toEntity} — o request vira argumentos primitivos do
 * usecase (o dono vem do usuário logado, nunca do corpo), sem entidade parcial como transporte.
 */
@Component
public class PreferenciaNotificacaoMapper {

    public PreferenciaNotificacaoResponse toResponse(PreferenciaNotificacao entity) {
        return new PreferenciaNotificacaoResponse(
                entity.getPush(),
                entity.getEmail(),
                entity.getSms(),
                entity.getWhatsapp());
    }
}
