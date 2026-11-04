package com.limio.api.comum.portas;

import com.limio.api.comum.localizacao.enums.Uf;

public record EnderecoLocalizado(String bairroRegiao, String cidade, Uf uf, String enderecoCompleto, double latitude,
        double longitude) {
}
