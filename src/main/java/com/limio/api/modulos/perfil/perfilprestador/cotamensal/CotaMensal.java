package com.limio.api.modulos.perfil.perfilprestador.cotamensal;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Quantas escolhas o Prestador FREE já consumiu num mês (ETI-79). Subentidade de {@link PerfilPrestador}, uma linha
 * por perfil e mês civil de {@link #FUSO}. Mês sem linha é um mês sem escolha consumida — é assim que a cota
 * "zera" na virada: nada é apagado, a contagem do mês novo só começa quando a primeira escolha a cria.
 *
 * <p>{@code perfilPrestadorId} é UUID cru (com FK no banco): a cota não é carregada junto do perfil, então não
 * entra na coleção dele como as janelas. Sem setter: a contagem só muda pela escolha (ETI-69/ETI-70) e pelo
 * estorno (ETI-94/ETI-95), que ganham método próprio aqui quando entrarem.
 */
@Entity
@Table(name = "cota_mensal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CotaMensal extends BaseEntity {

    /** O mês da cota é o calendário do Brasil: renova no dia 1, 00:00 de São Paulo, não de UTC. */
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Column(name = "perfil_prestador_id", nullable = false, updatable = false)
    private UUID perfilPrestadorId;

    @Convert(converter = AnoMesConverter.class)
    @Column(name = "ano_mes", nullable = false, updatable = false, length = 7)
    private YearMonth anoMes;

    @Column(name = "escolhas_consumidas", nullable = false)
    private int escolhasConsumidas;

    @Column(name = "teto_aplicado", nullable = false, updatable = false)
    private int tetoAplicado;

    private CotaMensal(UUID perfilPrestadorId, YearMonth anoMes, int tetoAplicado) {
        this.perfilPrestadorId = perfilPrestadorId;
        this.anoMes = anoMes;
        this.tetoAplicado = tetoAplicado;
    }

    /**
     * Cota de um mês que ainda não tem linha: nenhuma escolha consumida, com o teto FREE em vigor. Não persiste.
     *
     * @param perfilPrestadorId nulo quando o Prestador ainda nem salvou o perfil de atuação
     */
    public static CotaMensal zerada(UUID perfilPrestadorId, YearMonth anoMes, int tetoFree) {
        return new CotaMensal(perfilPrestadorId, anoMes, tetoFree);
    }

    /** Mês da cota em que {@code instante} cai. */
    public static YearMonth mesDe(Instant instante) {
        return YearMonth.from(instante.atZone(FUSO));
    }

    /** Dia 1 do mês, 00:00 de {@link #FUSO}: a partir daqui as escolhas contam neste mês. */
    public Instant inicio() {
        return anoMes.atDay(1).atStartOfDay(FUSO).toInstant();
    }

    /** Dia 1 do mês seguinte, 00:00 de {@link #FUSO}: a contagem recomeça do zero. */
    public Instant renovaEm() {
        return anoMes.plusMonths(1).atDay(1).atStartOfDay(FUSO).toInstant();
    }

    /** Escolhas consumidas chegaram ao teto: nova candidatura é recusada até {@link #renovaEm()} (ETI-75/ETI-76). */
    public boolean isEsgotada() {
        return escolhasConsumidas >= tetoAplicado;
    }
}
