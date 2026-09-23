package com.limio.api.modulos.auth.actions.helper;

/** Validação de formato e dígitos verificadores de CPF — função pura, sem estado. */
public final class CpfValidator {

    private CpfValidator() {
    }

    public static boolean isValido(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}") || todosDigitosIguais(cpf)) {
            return false;
        }
        int primeiroDigito = calcularDigitoVerificador(cpf.substring(0, 9), 10);
        int segundoDigito = calcularDigitoVerificador(cpf.substring(0, 9) + primeiroDigito, 11);
        return cpf.equals(cpf.substring(0, 9) + primeiroDigito + segundoDigito);
    }

    private static boolean todosDigitosIguais(String cpf) {
        return cpf.chars().distinct().count() == 1;
    }

    private static int calcularDigitoVerificador(String base, int pesoInicial) {
        int soma = 0;
        int peso = pesoInicial;
        for (char c : base.toCharArray()) {
            soma += (c - '0') * peso--;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
