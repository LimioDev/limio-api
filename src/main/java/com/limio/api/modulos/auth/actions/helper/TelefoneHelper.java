package com.limio.api.modulos.auth.actions.helper;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TelefoneHelper {

    private static final String PREFIXO_PAIS = "+55";
    private static final Pattern CELULAR = Pattern.compile("^(?:55)?([1-9][1-9])(9\\d{8})$");

    private TelefoneHelper() {
    }

    public static Optional<String> normalizar(String entrada) {
        if (entrada == null) {
            return Optional.empty();
        }
        Matcher celular = CELULAR.matcher(entrada.replaceAll("\\D", ""));
        return celular.matches()
                ? Optional.of(celular.group(1) + celular.group(2))
                : Optional.empty();
    }

    /**
     * Saída (API e integrações): {@code +55} + o que está armazenado. Vale também para linhas
     * legadas de 10 dígitos (fixo) — o país é sempre o Brasil. Vazio se não há dígitos.
     */
    public static Optional<String> paraInternacional(String telefoneArmazenado) {
        if (telefoneArmazenado == null) {
            return Optional.empty();
        }
        String digitos = telefoneArmazenado.replaceAll("\\D", "");
        return digitos.isEmpty() ? Optional.empty() : Optional.of(PREFIXO_PAIS + digitos);
    }
}
