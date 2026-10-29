package com.limio.api.modulos.anuncio.records;

import com.limio.api.comum.localizacao.enums.Uf;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Endereço digitado na criação do anúncio, com os mesmos campos e limites do "Novo endereço"
 * ({@code EnderecoSalvoRequest}, ETI-50/ETI-51), sem apelido nem padrão. Sem latitude/longitude: o servidor as
 * obtém pelo geocodificador.
 */
public record EnderecoAvulsoRequest(
        @NotBlank(message = "{enderecoAnuncio.cep.obrigatorio}")
        @Pattern(regexp = "\\d{8}", message = "{enderecoAnuncio.cep.formato}")
        String cep,

        @NotBlank(message = "{enderecoAnuncio.rua.obrigatoria}")
        @Size(max = 80, message = "{enderecoAnuncio.rua.tamanho}")
        String rua,

        @NotBlank(message = "{enderecoAnuncio.numero.obrigatorio}")
        @Size(max = 10, message = "{enderecoAnuncio.numero.tamanho}")
        String numero,

        @Size(max = 40, message = "{enderecoAnuncio.complemento.tamanho}")
        String complemento,

        @NotBlank(message = "{enderecoAnuncio.bairroRegiao.obrigatorio}")
        @Size(max = 50, message = "{enderecoAnuncio.bairroRegiao.tamanho}")
        String bairroRegiao,

        @NotBlank(message = "{enderecoAnuncio.cidade.obrigatoria}")
        @Size(max = 50, message = "{enderecoAnuncio.cidade.tamanho}")
        String cidade,

        @NotNull(message = "{enderecoAnuncio.uf.obrigatoria}")
        Uf uf) {

    public EnderecoAvulsoRequest {
        cep = cep == null ? null : cep.replaceAll("\\D", "");
        rua = rua == null ? null : rua.strip();
        numero = numero == null ? null : numero.strip();
        complemento = complemento == null || complemento.isBlank() ? null : complemento.strip();
        bairroRegiao = bairroRegiao == null ? null : bairroRegiao.strip();
        cidade = cidade == null ? null : cidade.strip();
    }
}
