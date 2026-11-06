package com.limio.api.modulos.chat.enums;

/**
 * Por que o chat parou de receber mensagens. O front escolhe o texto da faixa (telas 22b a 22e) por este valor.
 * Hoje {@link #ANUNCIO_CANCELADO} (ETI-63), {@link #CONTA_ENCERRADA} (TICKET-0049) e {@link #CANDIDATO_DESISTIU}
 * (ETI-77) têm evento publicado e listener ligado; os demais entram quando os cards donos dos eventos ligarem seus
 * listeners em {@code DeixarChatSomenteLeituraUseCase}.
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
    /** {@code ServicoCancelado} (ETI-94 a ETI-97, Sprint 7). */
    SERVICO_CANCELADO,
    /** {@code ContaEncerrada} de uma das partes (TICKET-0049): a outra parte aparece como "Usuário excluído". */
    CONTA_ENCERRADA
}
