package com.limio.api.comum.localizacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.limio.api.comum.localizacao.enums.Uf;

class UfTest {

    private static final List<String> SIGLAS_OFICIAIS = List.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA",
            "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    @Test
    void deveTerExatamenteAs27SiglasOficiais() {
        assertThat(Arrays.stream(Uf.values()).map(Uf::name)).containsExactlyInAnyOrderElementsOf(SIGLAS_OFICIAIS);
        assertThat(Uf.values()).hasSize(27);
    }

    @Test
    void naoDeveAceitarSiglaForaDaLista() {
        assertThat(Arrays.stream(Uf.values()).map(Uf::name)).doesNotContain("XX", "sp", "BR");
    }
}
