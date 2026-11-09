package com.limio.api.comum.midia.enums;

/** Uso da mídia. Chat entra aqui quando o módulo existir. */
public enum TipoMidia {
    FOTO_PERFIL,
    FOTO_ANUNCIO,
    /**
     * Foto do check-out (ETI-90), lida por {@code ConsultarValidacaoUseCase} (ETI-92). Mesmo nome que a #33 (ETI-90)
     * grava; o check-out ainda não existe nesta branch.
     */
    EVIDENCIA_CHECKOUT
}
