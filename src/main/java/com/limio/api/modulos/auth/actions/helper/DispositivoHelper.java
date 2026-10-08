package com.limio.api.modulos.auth.actions.helper;

/**
 * Normaliza o {@code dispositivo} da {@code Sessao}: texto livre (informado
 * pelo app ou User-Agent) que vai pro banco. Sem caractere de controle (o
 * Postgres recusa byte nulo em coluna de texto — virava 500) e no máximo 255.
 */
public final class DispositivoHelper {

    private static final int TAMANHO_MAXIMO = 255;

    private DispositivoHelper() {
    }

    public static String normalizar(String informado) {
        if (informado == null) {
            return null;
        }
        String limpo = informado.replaceAll("\\p{Cntrl}", "").strip();
        if (limpo.isEmpty()) {
            return null;
        }
        return limpo.length() <= TAMANHO_MAXIMO ? limpo : limpo.substring(0, TAMANHO_MAXIMO);
    }
}
