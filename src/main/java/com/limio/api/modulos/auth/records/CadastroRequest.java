package com.limio.api.modulos.auth.records;

import java.time.LocalDate;
import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Texto de cada mensagem fica em {@code ValidationMessages.properties}
 * (chave entre chaves) — nenhuma string de negócio hardcoded na anotação.
 */
public record CadastroRequest(
        @NotBlank(message = "{cadastro.nomeCompleto.obrigatorio}")
        String nomeCompleto,

        @NotBlank(message = "{cadastro.email.obrigatorio}")
        @Email(message = "{cadastro.email.formato}")
        @Size(max = 255, message = "{cadastro.email.tamanho}")
        String email,

        @NotBlank(message = "{cadastro.telefone.obrigatorio}")
        @Pattern(regexp = "\\d{10,11}", message = "{cadastro.telefone.formato}")
        String telefone,

        @NotBlank(message = "{cadastro.senha.obrigatoria}")
        @Size(min = 8, message = "{cadastro.senha.tamanho}")
        String senha,

        @NotBlank(message = "{cadastro.cpf.obrigatorio}")
        String cpf,

        @NotNull(message = "{cadastro.dataNascimento.obrigatoria}")
        @Past(message = "{cadastro.dataNascimento.passado}")
        LocalDate dataNascimento,

        @NotBlank(message = "{cadastro.cidadeUf.obrigatoria}")
        String cidadeUf) {

    public CadastroRequest {
        nomeCompleto = normalizar(nomeCompleto);
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        telefone = somenteDigitos(telefone);
        cpf = somenteDigitos(cpf);
        cidadeUf = normalizar(cidadeUf);
    }

    private static String normalizar(String valor) {
        return valor == null ? null : valor.trim();
    }

    private static String somenteDigitos(String valor) {
        return valor == null ? null : valor.replaceAll("\\D", "");
    }
}
