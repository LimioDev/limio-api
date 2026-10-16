package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.limio.api.modulos.auth.actions.helper.TelefoneHelper;

class TelefoneHelperTest {

    @ParameterizedTest
    @ValueSource(strings = { "11999998888", "(11) 99999-8888", "+55 11 99999-8888", "5511999998888", "+5511999998888",
            "  11 9 9999 8888  " })
    void deveNormalizarCelularParaDigitosNacionais(String entrada) {
        assertThat(TelefoneHelper.normalizar(entrada)).contains("11999998888");
    }

    @Test
    void deveAceitarDddCinquentaECincoSemConfundirComPrefixoDoPais() {
        assertThat(TelefoneHelper.normalizar("55999998888")).contains("55999998888");
        assertThat(TelefoneHelper.normalizar("5555999998888")).contains("55999998888");
    }

    @ParameterizedTest
    @ValueSource(strings = { "1133334444", "11 3333-4444", "11899998888", "01999998888", "10999998888", "00999998888",
            "1199999888", "119999988888", "551199999888", "abc", "+1 415 555 2671" })
    void deveRecusarFixoDddInvalidoOuTamanhoErrado(String entrada) {
        assertThat(TelefoneHelper.normalizar(entrada)).isEqualTo(Optional.empty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   " })
    void deveRecusarVazioOuNulo(String entrada) {
        assertThat(TelefoneHelper.normalizar(entrada)).isEmpty();
    }

    @Test
    void paraInternacionalDeveAcrescentarOPrefixoDoPaisAosDigitosArmazenados() {
        assertThat(TelefoneHelper.paraInternacional("11999998888")).contains("+5511999998888");
    }

    @Test
    void paraInternacionalDeveAceitarLegadoDeDezDigitosEContaEncerrada() {
        assertThat(TelefoneHelper.paraInternacional("1133334444")).contains("+551133334444");
        assertThat(TelefoneHelper.paraInternacional("00000000000")).contains("+5500000000000");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "abc" })
    void paraInternacionalDeveDevolverVazioSemDigitos(String armazenado) {
        assertThat(TelefoneHelper.paraInternacional(armazenado)).isEmpty();
    }
}
