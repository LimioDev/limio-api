package com.limio.api.modulos.perfil.perfilprestador.cotamensal;

import java.util.List;

import com.limio.api.comum.portas.EscolhaDoPrestador;
import com.limio.api.comum.portas.enums.Plano;

/**
 * O que a tela "Cota do mês" mostra (ETI-79): o plano, a cota do mês corrente e os serviços em que o Prestador foi
 * escolhido nele. No GOLD a cota ainda vem (escolhas consumidas enquanto era FREE), mas o teto não se aplica.
 */
public record CotaDoMes(Plano plano, CotaMensal cota, List<EscolhaDoPrestador> escolhas) {

    public CotaDoMes {
        escolhas = List.copyOf(escolhas);
    }

    /** Teto de escolhas do mês; nulo no GOLD, que não tem teto. */
    public Integer teto() {
        return switch (plano) {
            case FREE -> cota.getTetoAplicado();
            case GOLD -> null;
        };
    }

    /** Cota esgotada: o FREE chegou ao teto. O GOLD nunca esgota. */
    public boolean isEsgotada() {
        return switch (plano) {
            case FREE -> cota.isEsgotada();
            case GOLD -> false;
        };
    }
}
