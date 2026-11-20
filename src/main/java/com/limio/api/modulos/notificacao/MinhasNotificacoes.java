package com.limio.api.modulos.notificacao;

import org.springframework.data.domain.Page;

/**
 * Resultado de listar as notificações (ETI-110): a página pedida e o total de não lidas do usuário inteiro (não só da
 * página), que é o número do sino da tela 13.
 */
public record MinhasNotificacoes(Page<Notificacao> pagina, long naoLidas) {
}
