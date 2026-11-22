package com.limio.api.modulos.administracao.actions.usecase;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaTotaisDeAnuncios;
import com.limio.api.comum.portas.ConsultaTotaisDeAssinaturas;
import com.limio.api.comum.portas.ConsultaTotaisDeDenuncias;
import com.limio.api.comum.portas.ConsultaTotaisDeUsuarios;
import com.limio.api.comum.portas.TotaisDeAnuncios;
import com.limio.api.modulos.administracao.NumerosDaOperacao;
import com.limio.api.modulos.administracao.enums.StatusOperacional;

/**
 * Consultar dashboard (ETI-117, somente leitura). Junta os totais de cada módulo pelas portas de leitura (ADR-0003):
 * {@code administracao} não lê tabela de outro módulo. Quem pode consultar (só o administrador) é conferido na rota,
 * pelo {@code SecurityConfig}.
 */
@Component
public class ConsultarDashboardUseCase {

    private final ConsultaTotaisDeUsuarios totaisDeUsuarios;
    private final ConsultaTotaisDeAnuncios totaisDeAnuncios;
    private final ConsultaTotaisDeAssinaturas totaisDeAssinaturas;
    private final ConsultaTotaisDeDenuncias totaisDeDenuncias;

    public ConsultarDashboardUseCase(ConsultaTotaisDeUsuarios totaisDeUsuarios,
            ConsultaTotaisDeAnuncios totaisDeAnuncios, ConsultaTotaisDeAssinaturas totaisDeAssinaturas,
            ConsultaTotaisDeDenuncias totaisDeDenuncias) {
        this.totaisDeUsuarios = totaisDeUsuarios;
        this.totaisDeAnuncios = totaisDeAnuncios;
        this.totaisDeAssinaturas = totaisDeAssinaturas;
        this.totaisDeDenuncias = totaisDeDenuncias;
    }

    /** Uma transação só leitura: as contagens saem do mesmo retrato da base. */
    @Transactional(readOnly = true)
    public NumerosDaOperacao executar() {
        TotaisDeAnuncios anuncios = totaisDeAnuncios.contar();
        return new NumerosDaOperacao(
                totaisDeUsuarios.contarContasNaoEncerradas(),
                anuncios.anuncios(),
                anuncios.candidaturas(),
                anuncios.escolhas(),
                totaisDeAssinaturas.contarGoldAtivas(),
                totaisDeDenuncias.contarAbertas(),
                StatusOperacional.OPERANDO_NORMALMENTE);
    }
}
