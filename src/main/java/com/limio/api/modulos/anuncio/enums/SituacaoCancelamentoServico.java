package com.limio.api.modulos.anuncio.enums;

/** O que o status do {@code Anuncio} diz sobre cancelar o serviço combinado (ETI-94). */
public enum SituacaoCancelamentoServico {

    /** {@code PUBLICADO}: combinado e ainda não começado. */
    CANCELAVEL,

    /** Em andamento ou aguardando validação: o caminho é a denúncia. */
    USAR_DENUNCIA,

    /** Concluído ou encerrado (inclui o cancelamento repetido): não há mais o que cancelar. */
    SEM_O_QUE_CANCELAR
}
