package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.anuncio.actions.helper.DistanciaHelper;

class DistanciaHelperTest {

    @Test
    void dadoMesmoPonto_quandoCalcula_entaoZero() {
        assertThat(DistanciaHelper.haversineKm(-22.9056, -47.0608, -22.9056, -47.0608)).isZero();
    }

    @Test
    void dadoUmGrauDeLatitude_quandoCalcula_entaoCercaDe111Km() {
        assertThat(DistanciaHelper.haversineKm(-22.0, -47.0, -23.0, -47.0)).isCloseTo(111.19, within(0.1));
    }

    @Test
    void dadoCampinasESaoPaulo_quandoCalcula_entaoCercaDe85Km() {
        // distância em linha reta conhecida entre os centros: ~84 km
        assertThat(DistanciaHelper.haversineKm(-22.9056, -47.0608, -23.5505, -46.6333)).isBetween(80.0, 90.0);
    }

    @Test
    void dadoOrdemTrocada_quandoCalcula_entaoMesmaDistancia() {
        assertThat(DistanciaHelper.haversineKm(-22.9, -47.06, -23.55, -46.63))
                .isEqualTo(DistanciaHelper.haversineKm(-23.55, -46.63, -22.9, -47.06));
    }
}
