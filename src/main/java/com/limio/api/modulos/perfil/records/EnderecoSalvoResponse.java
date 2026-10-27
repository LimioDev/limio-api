package com.limio.api.modulos.perfil.records;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.limio.api.comum.localizacao.enums.Uf;

public record EnderecoSalvoResponse(
        UUID id,
        String apelido,
        String cep,
        String rua,
        String numero,
        String complemento,
        String bairroRegiao,
        String cidade,
        Uf uf,
        double latitude,
        double longitude,
        @JsonProperty("isPadrao") boolean padrao) {
}
