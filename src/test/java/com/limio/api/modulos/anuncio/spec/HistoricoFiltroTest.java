package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.limio.api.modulos.anuncio.enums.StatusHistorico;
import com.limio.api.modulos.anuncio.records.HistoricoFiltro;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class HistoricoFiltroTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void criarValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void fecharFabrica() {
        fabrica.close();
    }

    private Set<ConstraintViolation<HistoricoFiltro>> validar(LocalDate inicio, LocalDate fim, Integer pagina,
            Integer tamanho) {
        return validador.validate(new HistoricoFiltro(StatusHistorico.CONCLUIDO, inicio, fim, pagina, tamanho));
    }

    @Test
    void dadoFiltroVazio_quandoCriar_entaoUsaPaginaZeroETamanhoPadrao() {
        HistoricoFiltro filtro = new HistoricoFiltro(null, null, null, null, null);

        assertThat(filtro.pagina()).isZero();
        assertThat(filtro.tamanho()).isEqualTo(HistoricoFiltro.TAMANHO_PADRAO);
        assertThat(validador.validate(filtro)).isEmpty();
    }

    @Test
    void dadoDataInicialPosteriorAFinal_quandoValidar_entaoRejeita() {
        assertThat(validar(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1), 0, 20)).hasSize(1);
    }

    @Test
    void dadoMesmoDiaNasDuasPontas_quandoValidar_entaoAceita() {
        assertThat(validar(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12), 0, 20)).isEmpty();
    }

    @Test
    void dadoSoUmaPontaDoPeriodo_quandoValidar_entaoAceita() {
        assertThat(validar(LocalDate.of(2026, 10, 12), null, 0, 20)).isEmpty();
        assertThat(validar(null, LocalDate.of(2026, 10, 12), 0, 20)).isEmpty();
    }

    @Test
    void dadoPaginaNegativa_quandoValidar_entaoRejeita() {
        assertThat(validar(null, null, -1, 20)).hasSize(1);
    }

    @Test
    void dadoTamanhoForaDoIntervalo_quandoValidar_entaoRejeita() {
        assertThat(validar(null, null, 0, 0)).hasSize(1);
        assertThat(validar(null, null, 0, HistoricoFiltro.TAMANHO_MAXIMO + 1)).hasSize(1);
        assertThat(validar(null, null, 0, HistoricoFiltro.TAMANHO_MAXIMO)).isEmpty();
    }
}
