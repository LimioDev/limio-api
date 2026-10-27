package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.perfil.enums.Uf;
import com.limio.api.modulos.perfil.records.EnderecoSalvoRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class EnderecoSalvoRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static EnderecoSalvoRequest valido() {
        return new EnderecoSalvoRequest("Casa", "01310100", "Avenida Paulista", "1000", "Apto 12",
                "Bela Vista", "São Paulo", Uf.SP, false);
    }

    private static EnderecoSalvoRequest comCep(String cep) {
        var base = valido();
        return new EnderecoSalvoRequest(base.apelido(), cep, base.rua(), base.numero(), base.complemento(),
                base.bairroRegiao(), base.cidade(), base.uf(), base.padrao());
    }

    private Set<String> camposInvalidos(EnderecoSalvoRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void deveAceitarRequestCompletoEComplementoOpcional() {
        assertThat(validator.validate(valido())).isEmpty();

        var semComplemento = new EnderecoSalvoRequest("Casa", "01310100", "Avenida Paulista", "S/N", null,
                "Bela Vista", "São Paulo", Uf.SP, true);
        assertThat(validator.validate(semComplemento)).isEmpty();
    }

    @Test
    void devePreservarPadraoNuloParaOEditarManterOValorAtual() {
        var request = new EnderecoSalvoRequest("Casa", "01310100", "Avenida Paulista", "1000", null,
                "Bela Vista", "São Paulo", Uf.SP, null);

        assertThat(request.padrao()).isNull();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void deveNormalizarTextosCepEComplementoEmBranco() {
        var request = new EnderecoSalvoRequest("  Casa ", "01310-100", " Avenida Paulista ", " 1000 ", "   ",
                " Bela Vista ", " São Paulo ", Uf.SP, false);

        assertThat(request.apelido()).isEqualTo("Casa");
        assertThat(request.cep()).isEqualTo("01310100");
        assertThat(request.rua()).isEqualTo("Avenida Paulista");
        assertThat(request.numero()).isEqualTo("1000");
        assertThat(request.complemento()).isNull();
        assertThat(request.bairroRegiao()).isEqualTo("Bela Vista");
        assertThat(request.cidade()).isEqualTo("São Paulo");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void deveExigirCepComExatamenteOitoDigitos() {
        assertThat(camposInvalidos(comCep("1234567"))).containsExactly("cep");
        assertThat(camposInvalidos(comCep("123456789"))).containsExactly("cep");
        assertThat(camposInvalidos(comCep("abcdefgh"))).containsExactly("cep");
        assertThat(camposInvalidos(comCep(null))).containsExactly("cep");
        assertThat(camposInvalidos(comCep("01310100"))).isEmpty();
    }

    @Test
    void deveRecusarCamposObrigatoriosVaziosEUfNula() {
        var vazio = new EnderecoSalvoRequest(" ", "01310100", null, " ", null, "", null, null, false);

        assertThat(camposInvalidos(vazio)).containsExactlyInAnyOrder("apelido", "rua", "numero", "bairroRegiao",
                "cidade", "uf");
    }

    @Test
    void deveRecusarTextosAcimaDoLimite() {
        var longo = new EnderecoSalvoRequest("a".repeat(61), "01310100", "a".repeat(256), "1".repeat(21),
                "a".repeat(101), "a".repeat(101), "a".repeat(101), Uf.SP, false);

        assertThat(camposInvalidos(longo)).containsExactlyInAnyOrder("apelido", "rua", "numero", "complemento",
                "bairroRegiao", "cidade");
    }
}
