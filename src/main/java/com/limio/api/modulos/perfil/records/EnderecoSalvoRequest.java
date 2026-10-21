package com.limio.api.modulos.perfil.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.limio.api.modulos.perfil.enums.Uf;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST} e {@code PUT /conta/perfil-empregador/enderecos}. Latitude e longitude vêm do
 * cliente (o app já geocodifica na tela "Novo endereço"); o backend só valida a faixa. {@code isPadrao}
 * ausente vale {@code false} (por isso {@code Boolean}: o Jackson 3 recusa primitivo ausente).
 */
public record EnderecoSalvoRequest(
        @NotBlank(message = "{enderecoSalvo.apelido.obrigatorio}")
        @Size(max = 60, message = "{enderecoSalvo.apelido.tamanho}")
        String apelido,

        @NotBlank(message = "{enderecoSalvo.cep.obrigatorio}")
        @Pattern(regexp = "\\d{8}", message = "{enderecoSalvo.cep.formato}")
        String cep,

        @NotBlank(message = "{enderecoSalvo.rua.obrigatoria}")
        @Size(max = 255, message = "{enderecoSalvo.rua.tamanho}")
        String rua,

        @NotBlank(message = "{enderecoSalvo.numero.obrigatorio}")
        @Size(max = 20, message = "{enderecoSalvo.numero.tamanho}")
        String numero,

        @Size(max = 100, message = "{enderecoSalvo.complemento.tamanho}")
        String complemento,

        @NotBlank(message = "{enderecoSalvo.bairroRegiao.obrigatorio}")
        @Size(max = 100, message = "{enderecoSalvo.bairroRegiao.tamanho}")
        String bairroRegiao,

        @NotBlank(message = "{enderecoSalvo.cidade.obrigatoria}")
        @Size(max = 100, message = "{enderecoSalvo.cidade.tamanho}")
        String cidade,

        @NotNull(message = "{enderecoSalvo.uf.obrigatoria}")
        Uf uf,

        @NotNull(message = "{enderecoSalvo.latitude.obrigatoria}")
        @DecimalMin(value = "-90", message = "{enderecoSalvo.latitude.faixa}")
        @DecimalMax(value = "90", message = "{enderecoSalvo.latitude.faixa}")
        Double latitude,

        @NotNull(message = "{enderecoSalvo.longitude.obrigatoria}")
        @DecimalMin(value = "-180", message = "{enderecoSalvo.longitude.faixa}")
        @DecimalMax(value = "180", message = "{enderecoSalvo.longitude.faixa}")
        Double longitude,

        @JsonProperty("isPadrao")
        Boolean padrao) {

    public EnderecoSalvoRequest {
        apelido = apelido == null ? null : apelido.strip();
        cep = cep == null ? null : cep.replaceAll("\\D", "");
        rua = rua == null ? null : rua.strip();
        numero = numero == null ? null : numero.strip();
        complemento = complemento == null || complemento.isBlank() ? null : complemento.strip();
        bairroRegiao = bairroRegiao == null ? null : bairroRegiao.strip();
        cidade = cidade == null ? null : cidade.strip();
        padrao = Boolean.TRUE.equals(padrao);
    }
}
