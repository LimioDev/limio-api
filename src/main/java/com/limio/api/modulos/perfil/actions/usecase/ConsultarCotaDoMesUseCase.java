package com.limio.api.modulos.perfil.actions.usecase;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.limio.api.comum.portas.ConsultaEscolhasDoPrestador;
import com.limio.api.comum.portas.ConsultaPlanoPrestador;
import com.limio.api.comum.portas.EscolhaDoPrestador;
import com.limio.api.comum.portas.enums.Plano;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.perfil.excecao.SomentePrestadorConsultaCotaException;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaDoMes;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensal;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensalService;

/**
 * Regra de negócio do US24 (consultar a cota do mês). Só lê: o mês corrente vem do {@link Clock} no fuso de
 * {@link CotaMensal#FUSO}, e um mês sem linha em {@code cota_mensal} é devolvido zerado, com o teto FREE em vigor —
 * é isso que faz a cota recomeçar do zero no dia 1, 00:00, sem job de virada.
 *
 * <p>O plano vem do {@code financeiro} e os serviços do mês do {@code anuncio}, por porta (ADR-0003). Não conhece
 * HTTP nem record.
 */
@Service
public class ConsultarCotaDoMesUseCase {

    private final PerfilPrestadorService perfilPrestadorService;
    private final CotaMensalService cotaMensalService;
    private final ConsultaPlanoPrestador consultaPlanoPrestador;
    private final ConsultaEscolhasDoPrestador consultaEscolhasDoPrestador;
    private final Clock relogio;
    private final int tetoFree;

    public ConsultarCotaDoMesUseCase(PerfilPrestadorService perfilPrestadorService,
            CotaMensalService cotaMensalService,
            ConsultaPlanoPrestador consultaPlanoPrestador,
            ConsultaEscolhasDoPrestador consultaEscolhasDoPrestador,
            Clock relogio,
            @Value("${perfil.cota.teto-free}") int tetoFree) {
        this.perfilPrestadorService = perfilPrestadorService;
        this.cotaMensalService = cotaMensalService;
        this.consultaPlanoPrestador = consultaPlanoPrestador;
        this.consultaEscolhasDoPrestador = consultaEscolhasDoPrestador;
        this.relogio = relogio;
        this.tetoFree = tetoFree;
    }

    public CotaDoMes executar(UUID usuarioId, PapelUsuario papelAtivo) {
        exigirPrestador(papelAtivo);

        YearMonth mes = CotaMensal.mesDe(relogio.instant());
        Optional<UUID> perfilId = perfilPrestadorService.buscarIdDoUsuario(usuarioId);
        CotaMensal cota = perfilId.flatMap(id -> cotaMensalService.buscarDoMes(id, mes))
                .orElseGet(() -> CotaMensal.zerada(perfilId.orElse(null), mes, tetoFree));

        Plano plano = consultaPlanoPrestador.planoAtual(usuarioId);
        List<EscolhaDoPrestador> escolhas = consultaEscolhasDoPrestador.listarEscolhidasEntre(usuarioId,
                cota.inicio(), cota.renovaEm());
        return new CotaDoMes(plano, cota, escolhas);
    }

    private static void exigirPrestador(PapelUsuario papelAtivo) {
        switch (papelAtivo) {
            case PRESTADOR -> {
            }
            case EMPREGADOR -> throw new SomentePrestadorConsultaCotaException();
        }
    }
}
