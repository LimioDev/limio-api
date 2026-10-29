package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.anuncio.actions.helper.JanelaDoMuralHelper;
import com.limio.api.modulos.anuncio.actions.helper.JanelaDoMuralHelper.Janela;
import com.limio.api.modulos.anuncio.enums.QuandoMural;

/** Filtro "Quando" do mural (ETI-79). 29/10/2026 é uma quinta-feira; semana de segunda a domingo, fuso de Brasília. */
class JanelaDoMuralHelperTest {

    private static Instant sp(int ano, int mes, int dia, int hora) {
        return LocalDateTime.of(ano, mes, dia, hora, 0).atZone(JanelaDoMuralHelper.FUSO).toInstant();
    }

    private static final Instant QUINTA = sp(2026, 10, 29, 10);
    private static final Instant PROXIMA_SEGUNDA = sp(2026, 11, 2, 0);

    @Test
    void dadoQualquer_quandoCalculaJanela_entaoComecaAgoraESemLimite() {
        assertThat(JanelaDoMuralHelper.de(QuandoMural.QUALQUER, QUINTA)).isEqualTo(new Janela(QUINTA, null));
    }

    @Test
    void dadoQuinta_quandoEstaSemana_entaoVaiAteASegundaSeguinte() {
        assertThat(JanelaDoMuralHelper.de(QuandoMural.ESTA_SEMANA, QUINTA)).isEqualTo(new Janela(QUINTA, PROXIMA_SEGUNDA));
    }

    @Test
    void dadoDomingo_quandoEstaSemana_entaoSoOQueFalta() {
        Instant domingo = sp(2026, 11, 1, 10);

        assertThat(JanelaDoMuralHelper.de(QuandoMural.ESTA_SEMANA, domingo)).isEqualTo(new Janela(domingo, PROXIMA_SEGUNDA));
    }

    @Test
    void dadoSegunda_quandoEstaSemana_entaoVaiAteASegundaDaSemanaSeguinte() {
        Instant segunda = sp(2026, 11, 2, 10);

        assertThat(JanelaDoMuralHelper.de(QuandoMural.ESTA_SEMANA, segunda)).isEqualTo(new Janela(segunda, sp(2026, 11, 9, 0)));
    }

    @Test
    void dadoQuinta_quandoFimDeSemana_entaoSabadoZeroHoraAteSegunda() {
        assertThat(JanelaDoMuralHelper.de(QuandoMural.FIM_DE_SEMANA, QUINTA))
                .isEqualTo(new Janela(sp(2026, 10, 31, 0), PROXIMA_SEGUNDA));
    }

    @Test
    void dadoSabado_quandoFimDeSemana_entaoComecaAgoraENaoNoSabadoZeroHora() {
        Instant sabado = sp(2026, 10, 31, 15);

        assertThat(JanelaDoMuralHelper.de(QuandoMural.FIM_DE_SEMANA, sabado)).isEqualTo(new Janela(sabado, PROXIMA_SEGUNDA));
    }

    @Test
    void dadoDomingo_quandoFimDeSemana_entaoAindaEEsteFimDeSemana() {
        Instant domingo = sp(2026, 11, 1, 10);

        assertThat(JanelaDoMuralHelper.de(QuandoMural.FIM_DE_SEMANA, domingo)).isEqualTo(new Janela(domingo, PROXIMA_SEGUNDA));
    }

    @Test
    void dadoMeiaNoiteUtcDeSabado_quandoFimDeSemana_entaoUsaODiaDeBrasiliaNaoOUtc() {
        // sábado 01:00 UTC ainda é sexta 22:00 em Brasília -> o fim de semana é o de amanhã
        Instant sextaNoite = Instant.parse("2026-10-31T01:00:00Z");

        assertThat(JanelaDoMuralHelper.de(QuandoMural.FIM_DE_SEMANA, sextaNoite).de()).isEqualTo(sp(2026, 10, 31, 0));
    }
}
