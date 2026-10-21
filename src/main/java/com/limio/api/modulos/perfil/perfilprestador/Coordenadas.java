package com.limio.api.modulos.perfil.perfilprestador;

import jakarta.persistence.Embeddable;

/**
 * Ponto em graus decimais (WGS84) — centro do município-base, resultado da geocodificação. Embutido no
 * {@link PerfilPrestador} (colunas {@code latitude_base}/{@code longitude_base}).
 *
 * @throws IllegalArgumentException latitude fora de -90..90 ou longitude fora de -180..180 (inclusive NaN)
 */
@Embeddable
public record Coordenadas(double latitude, double longitude) {

    public Coordenadas {
        if (!(Math.abs(latitude) <= 90) || !(Math.abs(longitude) <= 180)) {
            throw new IllegalArgumentException("coordenadas fora da faixa: " + latitude + ", " + longitude);
        }
    }
}
