package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.limio.api.modulos.notificacao.records.PreferenciaNotificacaoRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class PreferenciaNotificacaoRequestTest {

    private static ValidatorFactory fabrica;
    private static Validator validator;

    @BeforeAll
    static void criarValidator() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validator = fabrica.getValidator();
    }

    @AfterAll
    static void fecharValidator() {
        fabrica.close();
    }

    private static Set<String> camposInvalidos(PreferenciaNotificacaoRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void deveAceitarOsQuatroCanaisPreenchidos() {
        assertThat(camposInvalidos(new PreferenciaNotificacaoRequest(false, false, false, false))).isEmpty();
    }

    @Test
    void deveRecusarCadaCanalAusente() {
        assertThat(camposInvalidos(new PreferenciaNotificacaoRequest(null, null, null, null)))
                .containsExactlyInAnyOrder("push", "email", "sms", "whatsapp");
    }

    @Test
    void deveRecusarSoOCanalAusente() {
        assertThat(camposInvalidos(new PreferenciaNotificacaoRequest(true, false, null, true)))
                .containsExactly("sms");
    }

    @Test
    void mensagemDeveVirDoValidationMessages() {
        String mensagem = validator.validate(new PreferenciaNotificacaoRequest(true, true, true, null))
                .iterator().next().getMessage();

        assertThat(mensagem).isEqualTo("whatsapp é obrigatório (true ou false)");
    }
}
