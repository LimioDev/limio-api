package com.limio.api.modulos.perfil.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.limio.api.modulos.perfil.enums.Uf;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST} e {@code PUT /conta/perfil-empregador/enderecos}. Não há latitude/longitude:
 * o servidor as obtém pelo geocodificador (propriedade enviada pelo cliente é ignorada). {@code isPadrao} é
 * {@code Boolean} (o Jackson 3 recusa primitivo ausente) e {@code null} significa "não enviado": no {@code POST}
 * vale {@code false}; no {@code PUT} mantém o padrão como está (só um {@code true}/{@code false} explícito o muda).
 */
public record EnderecoSalvoRequest(
        @NotBlank(message = "{enderecoSalvo.apelido.obrigatorio}")
        @Size(max = 60, message = "{enderecoSalvo.apelido.tamanho}")
        String apelido,

        @NotBlank(message = "{enderecoSalvo.cep.obrigatorio}")
        @Pattern(regexp = "\\d{8}", message = "{enderecoSalvo.cep.formato}")
        String cep,

        @NotBlank(message = "{enderecoSalvo.rua.obrigatoria}")
        @Size(max = 80, message = "{enderecoSalvo.rua.tamanho}")
        String rua,

        @NotBlank(message = "{enderecoSalvo.numero.obrigatorio}")
        @Size(max = 10, message = "{enderecoSalvo.numero.tamanho}")
        String numero,

        @Size(max = 40, message = "{enderecoSalvo.complemento.tamanho}")
        String complemento,

        @NotBlank(message = "{enderecoSalvo.bairroRegiao.obrigatorio}")
        @Size(max = 50, message = "{enderecoSalvo.bairroRegiao.tamanho}")
        String bairroRegiao,

        @NotBlank(message = "{enderecoSalvo.cidade.obrigatoria}")
        @Size(max = 50, message = "{enderecoSalvo.cidade.tamanho}")
        String cidade,

        @NotNull(message = "{enderecoSalvo.uf.obrigatoria}")
        Uf uf,

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
    }
}
