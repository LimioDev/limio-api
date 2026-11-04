package com.limio.api.comum.midia.enums;

/** Uso da mídia. Chat entra aqui quando o módulo existir. */
public enum TipoMidia {
    FOTO_PERFIL,
    FOTO_ANUNCIO,
    /** Foto do check-out (ETI-90); gravada só quando o check-out existir, lida já por {@code ConsultarValidacaoUseCase} (ETI-92). */
    FOTO_CHECKOUT
}
