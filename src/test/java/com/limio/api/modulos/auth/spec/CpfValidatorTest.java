package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.limio.api.modulos.auth.actions.helper.CpfValidator;

class CpfValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = { "52998224725", "11144477735" })
    void deveAceitarCpfComDigitosVerificadoresCorretos(String cpf) {
        assertThat(CpfValidator.isValido(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "52998224726", "00000000000", "11111111111", "123", "abcdefghijk", "" })
    void deveRecusarCpfInvalido(String cpf) {
        assertThat(CpfValidator.isValido(cpf)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void deveRecusarCpfNulo() {
        assertThat(CpfValidator.isValido(null)).isFalse();
    }
}
