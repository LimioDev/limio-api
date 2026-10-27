package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.anuncio.actions.helper.NomeHelper;

class NomeHelperTest {

    @Test
    void dadoNomeESobrenome_quandoAbrevia_entaoPrimeiroNomeEInicialDoSobrenome() {
        assertThat(NomeHelper.abreviar("Rafael Souza")).isEqualTo("Rafael S.");
    }

    @Test
    void dadoVariosSobrenomes_quandoAbrevia_entaoUsaOUltimo() {
        assertThat(NomeHelper.abreviar("Rafael da Silva Souza")).isEqualTo("Rafael S.");
    }

    @Test
    void dadoSobrenomeMinusculoEEspacosSobrando_quandoAbrevia_entaoNormaliza() {
        assertThat(NomeHelper.abreviar("  Rafael   souza ")).isEqualTo("Rafael S.");
    }

    @Test
    void dadoNomeDeUmaPalavra_quandoAbrevia_entaoSaiComoEsta() {
        assertThat(NomeHelper.abreviar("Rafael")).isEqualTo("Rafael");
    }

    @Test
    void dadoVariosSobrenomes_quandoTiraIniciais_entaoPrimeiroNomeEUltimoSobrenome() {
        assertThat(NomeHelper.iniciais("Rafael da Silva Souza")).isEqualTo("RS");
    }

    @Test
    void dadoMinusculasEEspacosSobrando_quandoTiraIniciais_entaoMaiusculas() {
        assertThat(NomeHelper.iniciais("  rafael   souza ")).isEqualTo("RS");
    }

    @Test
    void dadoNomeDeUmaPalavra_quandoTiraIniciais_entaoUmaLetra() {
        assertThat(NomeHelper.iniciais("Rafael")).isEqualTo("R");
    }
}
