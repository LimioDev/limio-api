package com.limio.api.modulos.perfil.endereco;

import com.limio.api.modulos.perfil.enums.Uf;

public record EnderecoParaGeocodificar(String rua, String numero, String cidade, Uf uf) {
}
