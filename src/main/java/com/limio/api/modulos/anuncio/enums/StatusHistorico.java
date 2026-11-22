package com.limio.api.modulos.anuncio.enums;

import java.util.Optional;

/**
 * Como um anúncio em estado final aparece no histórico (ETI-71). Não é um status persistido: junta
 * {@link StatusAnuncio} e {@link CausaEncerramento}, porque "cancelado" e "expirado" são ambos
 * {@link StatusAnuncio#ENCERRADO}.
 */
public enum StatusHistorico {

    CONCLUIDO,
    CANCELADO,
    EXPIRADO;

    /**
     * Classificação de um anúncio no histórico. Vazio enquanto o anúncio ainda não chegou a um estado final (não
     * pertence ao histórico) e também se {@code ENCERRADO} vier sem causa, o que só aconteceria com dado anterior
     * ao backfill da causa. {@code switch} sem {@code default}: um valor novo obriga a decidir aqui.
     */
    public static Optional<StatusHistorico> de(StatusAnuncio status, CausaEncerramento causa) {
        return switch (status) {
            case CONCLUIDO -> Optional.of(CONCLUIDO);
            case ENCERRADO -> Optional.ofNullable(causa).map(StatusHistorico::deCausa);
            case PUBLICADO, EM_ANDAMENTO, AGUARDANDO_VALIDACAO -> Optional.empty();
        };
    }

    private static StatusHistorico deCausa(CausaEncerramento causa) {
        return switch (causa) {
            case CANCELAMENTO -> CANCELADO;
            case EXPIRACAO -> EXPIRADO;
        };
    }
}
