package com.limio.api.modulos.moderacao.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;
import com.limio.api.modulos.moderacao.enums.Severidade;

class SeveridadeTest {

    @Test
    void dinheiroEFraudeSaoAlta() {
        assertThat(Severidade.de(MotivoDenuncia.SUSPEITA_DE_FRAUDE)).isEqualTo(Severidade.ALTA);
        assertThat(Severidade.de(MotivoDenuncia.NAO_PAGOU_VALOR_COMBINADO)).isEqualTo(Severidade.ALTA);
        assertThat(Severidade.de(MotivoDenuncia.OUTRO_MOTIVO)).isEqualTo(Severidade.BAIXA);
    }

    @Test
    void todoMotivoCaiEmExatamenteUmaSeveridade() {
        long total = Arrays.stream(Severidade.values()).mapToLong(severidade -> severidade.motivos().size()).sum();

        assertThat(total).isEqualTo(MotivoDenuncia.values().length);
    }
}
