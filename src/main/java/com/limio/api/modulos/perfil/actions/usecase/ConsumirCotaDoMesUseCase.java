package com.limio.api.modulos.perfil.actions.usecase;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensal;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensalService;

/**
 * Regra de negócio do consumo da cota (ETI-69): uma escolha do Prestador FREE gasta uma das {@code teto} do mês.
 * Entra na transação de quem chama ({@code REQUIRED}) — o consumo e a escolha confirmam ou desfazem juntos.
 *
 * <p>A trava {@link EscopoTrava#COTA_PRESTADOR} (chave = Prestador) vem antes de ler o saldo e cobre também a
 * criação da linha do mês, que o {@code SELECT ... FOR UPDATE} não cobriria (linha inexistente não se trava).
 * Saldo esgotado não altera nada. O mês novo cria a linha só quando a primeira escolha chega (a cota "zera" sem job).
 */
@Service
public class ConsumirCotaDoMesUseCase {

    private final PerfilPrestadorService perfilPrestadorService;
    private final CotaMensalService cotaMensalService;
    private final TravaTransacional trava;
    private final int tetoFree;

    public ConsumirCotaDoMesUseCase(PerfilPrestadorService perfilPrestadorService,
            CotaMensalService cotaMensalService,
            TravaTransacional trava,
            @Value("${perfil.cota.teto-free}") int tetoFree) {
        this.perfilPrestadorService = perfilPrestadorService;
        this.cotaMensalService = cotaMensalService;
        this.trava = trava;
        this.tetoFree = tetoFree;
    }

    @Transactional
    public boolean executar(UUID prestadorId, Instant ocorridaEm) {
        trava.travar(EscopoTrava.COTA_PRESTADOR, prestadorId.toString());

        // Quem foi candidato já salvou o perfil (o mural exige). Sem perfil não há onde gravar a cota (FK).
        UUID perfilId = perfilPrestadorService.buscarIdDoUsuario(prestadorId)
                .orElseThrow(() -> new IllegalStateException("Prestador " + prestadorId + " sem perfil de atuação"));

        YearMonth mes = CotaMensal.mesDe(ocorridaEm);
        CotaMensal cota = cotaMensalService.buscarDoMes(perfilId, mes)
                .orElseGet(() -> CotaMensal.zerada(perfilId, mes, tetoFree));
        if (cota.isEsgotada()) {
            return false;
        }

        cota.consumirEscolha();
        cotaMensalService.salvar(cota);
        return true;
    }
}
