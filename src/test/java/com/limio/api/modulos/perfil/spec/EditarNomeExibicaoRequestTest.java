package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.records.EditarNomeExibicaoRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class EditarNomeExibicaoRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void deveAparaEspacosDasPontas() {
        assertThat(new EditarNomeExibicaoRequest("  Maria  ").nomeExibicao()).isEqualTo("Maria");
    }

    @Test
    void deveAceitarNomeNoLimiteDeTamanho() {
        var request = new EditarNomeExibicaoRequest("a".repeat(PerfilEmpregador.NOME_EXIBICAO_MAX));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void deveRecusarNomeNuloEmBrancoOuAcimaDoLimite() {
        assertThat(validator.validate(new EditarNomeExibicaoRequest(null))).hasSize(1);
        assertThat(validator.validate(new EditarNomeExibicaoRequest("   "))).hasSize(1);
        assertThat(validator.validate(new EditarNomeExibicaoRequest("a".repeat(PerfilEmpregador.NOME_EXIBICAO_MAX + 1))))
                .hasSize(1);
    }
}
