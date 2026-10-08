package com.limio.api.modulos.notificacao.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseMapper;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.records.PreferenciaNotificacaoRequest;
import com.limio.api.modulos.notificacao.records.PreferenciaNotificacaoResponse;

/**
 * Anti-corruption layer entre {@link PreferenciaNotificacao} e os records HTTP do módulo. Chamado só pelo
 * controller. {@code toEntity} não preenche {@code usuarioId}: quem é o dono vem do usuário logado, nunca do corpo.
 */
@Component
public class PreferenciaNotificacaoMapper
        implements BaseMapper<PreferenciaNotificacao, PreferenciaNotificacaoRequest, PreferenciaNotificacaoResponse> {

    @Override
    public PreferenciaNotificacao toEntity(PreferenciaNotificacaoRequest request) {
        return PreferenciaNotificacao.builder()
                .push(request.push())
                .email(request.email())
                .sms(request.sms())
                .whatsapp(request.whatsapp())
                .build();
    }

    @Override
    public PreferenciaNotificacaoResponse toResponse(PreferenciaNotificacao entity) {
        return new PreferenciaNotificacaoResponse(
                entity.getPush(),
                entity.getEmail(),
                entity.getSms(),
                entity.getWhatsapp());
    }
}
