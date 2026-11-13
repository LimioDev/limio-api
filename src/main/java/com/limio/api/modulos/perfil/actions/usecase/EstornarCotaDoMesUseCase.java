package com.limio.api.modulos.perfil.actions.usecase;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensal;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensalService;

/**
 * Regra de negócio do estorno da cota (ETI-94, RB17): o cancelamento normal do serviço combinado devolve ao
 * Prestador FREE a escolha que ele gastou. Entra na transação de quem chama ({@code REQUIRED}), como o consumo
 * ({@link ConsumirCotaDoMesUseCase}) — o estorno e o encerramento do serviço confirmam ou desfazem juntos.
 *
 * <p>Estorna na cota do mês de {@code escolhidaEm} (onde a escolha foi contada), mesmo que o cancelamento caia em
 * outro mês. Mesma trava {@link EscopoTrava#COTA_PRESTADOR} do consumo, para não correr contra uma escolha nova do
 * mesmo Prestador. Nunca cria linha: mês sem linha ou já zerado não tem o que devolver — devolve {@code false} e
 * registra aviso (estado inesperado, mas que não deve impedir o Empregador de cancelar).
 */
@Service
public class EstornarCotaDoMesUseCase {

    private static final Logger log = LoggerFactory.getLogger(EstornarCotaDoMesUseCase.class);

    private final PerfilPrestadorService perfilPrestadorService;
    private final CotaMensalService cotaMensalService;
    private final TravaTransacional trava;

    public EstornarCotaDoMesUseCase(PerfilPrestadorService perfilPrestadorService,
            CotaMensalService cotaMensalService,
            TravaTransacional trava) {
        this.perfilPrestadorService = perfilPrestadorService;
        this.cotaMensalService = cotaMensalService;
        this.trava = trava;
    }

    @Transactional
    public boolean executar(UUID prestadorId, Instant escolhidaEm) {
        trava.travar(EscopoTrava.COTA_PRESTADOR, prestadorId.toString());

        Optional<UUID> perfilId = perfilPrestadorService.buscarIdDoUsuario(prestadorId);
        if (perfilId.isEmpty()) {
            log.warn("estorno de cota sem perfil de atuação: prestador={}", prestadorId);
            return false;
        }

        YearMonth mes = CotaMensal.mesDe(escolhidaEm);
        Optional<CotaMensal> cota = cotaMensalService.buscarDoMes(perfilId.get(), mes);
        if (cota.isEmpty() || !cota.get().estornarEscolha()) {
            log.warn("estorno de cota sem escolha consumida a devolver: prestador={} mes={}", prestadorId, mes);
            return false;
        }

        cotaMensalService.salvar(cota.get());
        return true;
    }
}
