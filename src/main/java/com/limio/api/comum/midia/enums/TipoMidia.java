package com.limio.api.comum.midia.enums;

/** Uso da mídia. O chat entra aqui quando o módulo existir. */
public enum TipoMidia {
    FOTO_PERFIL,
    FOTO_ANUNCIO,
    /** Foto do check-out (ETI-90), lida por {@code ConsultarValidacaoUseCase} (ETI-92). */
    EVIDENCIA_CHECKOUT
}
