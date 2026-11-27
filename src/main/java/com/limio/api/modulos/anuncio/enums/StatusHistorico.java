package com.limio.api.modulos.anuncio.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Como um anúncio em estado final aparece no histórico (ETI-71). Não é um status persistido: junta
 * {@link StatusAnuncio} e {@link CausaEncerramento}, porque "cancelado" e "expirado" são ambos
 * {@link StatusAnuncio#ENCERRADO}. A ida ({@link #de}) e a volta ({@link #statusAceitos} e {@link #causasAceitas}, para
 * filtrar na consulta) ficam juntas aqui: mudar uma obriga a conferir a outra.
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

    /** Status de anúncio que o {@code filtro} abrange; sem filtro ({@code null}), todos os do histórico. */
    public static List<StatusAnuncio> statusAceitos(StatusHistorico filtro) {
        if (filtro == null) {
            return Arrays.stream(values()).map(StatusHistorico::statusDoAnuncio).distinct().toList();
        }
        return List.of(filtro.statusDoAnuncio());
    }

    /**
     * Causas que o {@code filtro} aceita entre os {@code ENCERRADO}. Nunca vazia (vai para um {@code in} da consulta):
     * sem filtro, ou em {@code CONCLUIDO}, que não tem causa nem é filtrado por ela, vêm todas.
     */
    public static List<CausaEncerramento> causasAceitas(StatusHistorico filtro) {
        List<CausaEncerramento> todas = List.of(CausaEncerramento.values());
        if (filtro == null) {
            return todas;
        }
        return switch (filtro) {
            case CONCLUIDO -> todas;
            case CANCELADO -> List.of(CausaEncerramento.CANCELAMENTO);
            case EXPIRADO -> List.of(CausaEncerramento.EXPIRACAO);
        };
    }

    private StatusAnuncio statusDoAnuncio() {
        return switch (this) {
            case CONCLUIDO -> StatusAnuncio.CONCLUIDO;
            case CANCELADO, EXPIRADO -> StatusAnuncio.ENCERRADO;
        };
    }

    private static StatusHistorico deCausa(CausaEncerramento causa) {
        return switch (causa) {
            case CANCELAMENTO -> CANCELADO;
            case EXPIRACAO -> EXPIRADO;
        };
    }
}
