package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.anuncio.actions.mapper.QuandoMuralConverter;
import com.limio.api.modulos.anuncio.enums.QuandoMural;

class QuandoMuralConverterTest {

    private final QuandoMuralConverter converter = new QuandoMuralConverter();

    @Test
    void dadoValoresDaTela_quandoConverte_entaoAceitaHifenEspacoESublinhado() {
        assertThat(converter.convert("qualquer")).isEqualTo(QuandoMural.QUALQUER);
        assertThat(converter.convert("esta-semana")).isEqualTo(QuandoMural.ESTA_SEMANA);
        assertThat(converter.convert("esta semana")).isEqualTo(QuandoMural.ESTA_SEMANA);
        assertThat(converter.convert("Fim_de_Semana")).isEqualTo(QuandoMural.FIM_DE_SEMANA);
    }

    @Test
    void dadoValorDesconhecido_quandoConverte_entaoFalhaParaVirar400() {
        assertThatThrownBy(() -> converter.convert("mes-que-vem")).isInstanceOf(IllegalArgumentException.class);
    }
}
