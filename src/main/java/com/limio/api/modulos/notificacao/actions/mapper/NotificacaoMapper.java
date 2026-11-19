package com.limio.api.modulos.notificacao.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.comum.records.PageResponse;
import com.limio.api.modulos.notificacao.MinhasNotificacoes;
import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.records.NaoLidasResponse;
import com.limio.api.modulos.notificacao.records.NotificacaoResponse;
import com.limio.api.modulos.notificacao.records.NotificacoesResponse;

@Component
public class NotificacaoMapper {

    public NotificacoesResponse toResponse(MinhasNotificacoes minhas) {
        return new NotificacoesResponse(PageResponse.de(minhas.pagina(), NotificacaoMapper::toResponse),
                minhas.naoLidas());
    }

    public NaoLidasResponse naoLidas(long naoLidas) {
        return new NaoLidasResponse(naoLidas);
    }

    private static NotificacaoResponse toResponse(Notificacao notificacao) {
        return new NotificacaoResponse(
                notificacao.getId(),
                notificacao.getTipo(),
                notificacao.getTitulo(),
                notificacao.getCorpo(),
                notificacao.getEnviadaEm(),
                notificacao.getLidaEm(),
                notificacao.getAnuncioId());
    }
}
