package com.limio.api.modulos.notificacao.enums;

/**
 * Do que trata a notificação (ETI-110). O app escolhe ícone e destino pelo tipo. Um valor por evento de domínio que
 * já pede aviso hoje; o envio (ETI-112) grava a notificação com o tipo do evento que a gerou e pode acrescentar
 * valores (a coluna não tem CHECK).
 */
public enum TipoNotificacao {
    /** {@code NovoServicoNaAreaEvent} (ETI-65): anúncio compatível publicado perto do Prestador. */
    NOVO_SERVICO_NA_AREA,
    /** {@code CandidaturaCriadaEvent} (ETI-75): novo candidato no anúncio do Empregador. */
    NOVA_CANDIDATURA,
    /** {@code PrestadorEscolhidoEvent} (ETI-69): o Prestador foi escolhido. */
    PRESTADOR_ESCOLHIDO,
    /** {@code CandidaturaRecusadaEvent} (ETI-67). */
    CANDIDATURA_RECUSADA,
    /** {@code CandidaturaEncerradaEvent}: anúncio fechado por outra escolha, cancelamento ou expiração. */
    CANDIDATURA_ENCERRADA,
    /** {@code AnuncioCanceladoEvent} (ETI-63). */
    ANUNCIO_CANCELADO,
    /** {@code ServicoIniciadoEvent} (ETI-88). */
    SERVICO_INICIADO,
    /** {@code CheckoutEnviadoEvent} (ETI-90): o Empregador precisa validar. */
    CHECKOUT_ENVIADO,
    /** {@code ServicoConcluidoEvent} (ETI-92). */
    SERVICO_CONCLUIDO,
    /** {@code AvaliacaoPublicadaEvent} (ETI-107). */
    AVALIACAO_RECEBIDA,
    /** {@code AvisoDeAnuncioEvent} (TICKET-0049): aviso genérico de anúncio, com o assunto no título. */
    AVISO_DE_ANUNCIO
}
