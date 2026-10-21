package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.auth.records.TrocarEmailRequest;
import com.limio.api.modulos.auth.records.TrocarTelefoneRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class TrocarContatoRequestsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void emailDeveSerNormalizadoParaMinusculoSemEspacos() {
        assertThat(new TrocarEmailRequest("  Maria@Example.COM ", "senha").novoEmail()).isEqualTo("maria@example.com");
    }

    @Test
    void emailValidoDeveAprovar() {
        assertThat(validator.validate(new TrocarEmailRequest("maria@example.com", "senha"))).isEmpty();
    }

    @Test
    void emailInvalidoVazioOuGrandeDeveReprovar() {
        assertThat(validator.validate(new TrocarEmailRequest("sem-arroba", "senha"))).hasSize(1);
        assertThat(validator.validate(new TrocarEmailRequest("   ", "senha"))).isNotEmpty();
        assertThat(validator.validate(new TrocarEmailRequest(null, "senha"))).isNotEmpty();
        assertThat(validator.validate(new TrocarEmailRequest("a".repeat(250) + "@example.com", "senha"))).isNotEmpty();
    }

    @Test
    void senhaAtualEmBrancoDeveReprovarNasDuasTrocas() {
        assertThat(validator.validate(new TrocarEmailRequest("maria@example.com", " "))).hasSize(1);
        assertThat(validator.validate(new TrocarTelefoneRequest("11999998888", ""))).hasSize(1);
    }

    @Test
    void telefoneValidoDeveChegarNormalizadoEmDigitosNacionais() {
        var request = new TrocarTelefoneRequest("(11) 99999-8888", "senha");

        assertThat(request.novoTelefone()).isEqualTo("11999998888");
        assertThat(new TrocarTelefoneRequest("+55 11 99999-8888", "senha").novoTelefone()).isEqualTo("11999998888");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void telefoneInvalidoDeveReprovarSemSerAlterado() {
        var fixo = new TrocarTelefoneRequest("1133334444", "senha");

        assertThat(fixo.novoTelefone()).isEqualTo("1133334444");
        assertThat(validator.validate(fixo)).hasSize(1);
        assertThat(validator.validate(new TrocarTelefoneRequest("", "senha"))).isNotEmpty();
        assertThat(validator.validate(new TrocarTelefoneRequest(null, "senha"))).isNotEmpty();
    }
}
