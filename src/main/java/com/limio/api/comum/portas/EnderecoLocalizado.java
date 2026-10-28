package com.limio.api.comum.portas;

import com.limio.api.comum.localizacao.enums.Uf;

/**
 * Endereço pronto pra virar retrato num anúncio: {@code bairroRegiao} e {@code uf} são os únicos dados que podem
 * ir pro mural; {@code enderecoCompleto} (uma linha, até 255 caracteres) e as coordenadas ficam só com o dono e
 * com quem for escolhido.
 */
public record EnderecoLocalizado(String bairroRegiao, Uf uf, String enderecoCompleto, double latitude,
        double longitude) {
}
