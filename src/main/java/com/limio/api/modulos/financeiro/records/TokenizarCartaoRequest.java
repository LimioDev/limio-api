package com.limio.api.modulos.financeiro.records;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Dados do cartão só passam por aqui pra tokenizar no gateway — nunca persistidos, nunca logados
 * (ETI-81). {@code holderNome}/{@code holderEmail}/{@code holderCpf} são do titular do cartão, que pode
 * não ser quem está logado.
 */
public record TokenizarCartaoRequest(
        @NotBlank(message = "{tokenizarCartao.holderName.obrigatorio}")
        String holderName,

        @NotBlank(message = "{tokenizarCartao.numero.obrigatorio}")
        @Pattern(regexp = "\\d{13,19}", message = "{tokenizarCartao.numero.formato}")
        String numero,

        @NotBlank(message = "{tokenizarCartao.expiryMonth.obrigatorio}")
        @Pattern(regexp = "\\d{2}", message = "{tokenizarCartao.expiryMonth.formato}")
        String expiryMonth,

        @NotBlank(message = "{tokenizarCartao.expiryYear.obrigatorio}")
        @Pattern(regexp = "\\d{4}", message = "{tokenizarCartao.expiryYear.formato}")
        String expiryYear,

        @NotBlank(message = "{tokenizarCartao.ccv.obrigatorio}")
        @Pattern(regexp = "\\d{3,4}", message = "{tokenizarCartao.ccv.formato}")
        String ccv,

        @NotBlank(message = "{tokenizarCartao.holderNome.obrigatorio}")
        String holderNome,

        @NotBlank(message = "{tokenizarCartao.holderEmail.obrigatorio}")
        @Email(message = "{tokenizarCartao.holderEmail.formato}")
        String holderEmail,

        @NotBlank(message = "{tokenizarCartao.holderCpf.obrigatorio}")
        @Pattern(regexp = "\\d{11}", message = "{tokenizarCartao.holderCpf.formato}")
        String holderCpf,

        @NotBlank(message = "{tokenizarCartao.holderCep.obrigatorio}")
        @Pattern(regexp = "\\d{8}", message = "{tokenizarCartao.holderCep.formato}")
        String holderCep,

        @NotBlank(message = "{tokenizarCartao.holderNumeroEndereco.obrigatorio}")
        String holderNumeroEndereco,

        @NotBlank(message = "{tokenizarCartao.holderTelefone.obrigatorio}")
        String holderTelefone) {
}
