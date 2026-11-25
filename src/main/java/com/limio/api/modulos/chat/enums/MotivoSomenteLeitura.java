package com.limio.api.modulos.chat.enums;

/**
 * Por que o chat parou de receber mensagens. O front escolhe o texto da faixa (telas 22b a 22e) por este valor.
 * Todos têm evento publicado e listener ligado em {@code DeixarChatSomenteLeituraUseCase}. {@link
 * #SERVICO_CANCELADO} vale para o cancelamento pelo Empregador (ETI-94) e pelo Prestador (ETI-96).
 */
public enum MotivoSomenteLeitura {
    /** {@code CandidaturaRecusada} (ETI-67/ETI-68). */
    CANDIDATURA_RECUSADA,
    /** {@code CandidaturaEncerrada} por desistência do Prestador (ETI-77/ETI-78). */
    CANDIDATO_DESISTIU,
    /** {@code PrestadorEscolhido}, para os chats dos não escolhidos (ETI-69/ETI-70). */
    OUTRO_PRESTADOR_ESCOLHIDO,
    /** {@code AnuncioCancelado} (ETI-63/ETI-64). */
    ANUNCIO_CANCELADO,
    /** {@code ServicoCanceladoEvent} (ETI-94) e {@code ServicoCanceladoPeloPrestadorEvent} (ETI-96). */
    SERVICO_CANCELADO,
    /** {@code ContaEncerrada} de uma das partes (TICKET-0049): a outra parte aparece como "Usuário excluído". */
    CONTA_ENCERRADA
}
