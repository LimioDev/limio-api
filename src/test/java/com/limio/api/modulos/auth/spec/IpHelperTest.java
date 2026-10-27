package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.limio.api.modulos.auth.actions.helper.IpHelper;

class IpHelperTest {

    @ParameterizedTest
    @ValueSource(strings = { "203.0.113.10", "8.8.8.8", "2001:4860:4860::8888" })
    void ipDeInternetEhPublico(String ip) {
        assertThat(IpHelper.ehPublico(ip)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "127.0.0.1", "10.1.2.3", "172.20.0.1", "192.168.0.10", "100.64.0.1", "100.127.255.1",
            "169.254.1.1", "::1", "fd12:3456::1", "fe80::1", "0.0.0.0" })
    void ipInternoNaoEhPublico(String ip) {
        assertThat(IpHelper.ehPublico(ip)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "abc", "exemplo.com", "999.1" })
    void textoQueNaoEhIpNaoEhPublicoENaoConsultaDns(String ip) {
        assertThat(IpHelper.ehPublico(ip)).isFalse();
    }
}
