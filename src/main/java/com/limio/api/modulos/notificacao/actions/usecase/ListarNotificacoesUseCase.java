package com.limio.api.modulos.notificacao.actions.usecase;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.notificacao.MinhasNotificacoes;
import com.limio.api.modulos.notificacao.NotificacaoService;

/**
 * UC "Ver minhas notificações" (ETI-110, US55): as notificações do usuário logado, da mais nova pra mais antiga, e o
 * total de não lidas (sino da tela 13). Vale pros dois papéis: o dono vem do token e o papel ativo não filtra.
 */
@Service
public class ListarNotificacoesUseCase {

    static final int TAMANHO_PAGINA = 20;

    private final NotificacaoService notificacaoService;

    public ListarNotificacoesUseCase(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @Transactional(readOnly = true)
    public MinhasNotificacoes executar(UUID usuarioId, int pagina) {
        return new MinhasNotificacoes(
                notificacaoService.buscarDoUsuario(usuarioId, PageRequest.of(pagina, TAMANHO_PAGINA)),
                notificacaoService.contarNaoLidas(usuarioId));
    }
}
