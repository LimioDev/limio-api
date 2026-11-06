package com.limio.api.modulos.anuncio.actions.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.modulos.anuncio.excecao.CategoriaIndisponivelException;
import com.limio.api.modulos.anuncio.excecao.DataNoPassadoException;

/**
 * Regras de conteúdo do anúncio que valem na criação (ETI-59) e na edição (ETI-61) e não cabem em Bean Validation
 * porque dependem do relógio ou de outro módulo. Uma só fonte pras duas, pra não divergirem.
 */
@Component
public class ValidacaoDoAnuncioService {

    private final ConsultaCategoria consultaCategoria;

    public ValidacaoDoAnuncioService(ConsultaCategoria consultaCategoria) {
        this.consultaCategoria = consultaCategoria;
    }

    public void exigirDataFutura(Instant dataHoraPretendida) {
        if (!dataHoraPretendida.isAfter(Instant.now())) {
            throw new DataNoPassadoException();
        }
    }

    public void exigirCategoriaAtiva(UUID categoriaId) {
        consultaCategoria.buscarPorId(categoriaId)
                .filter(ResumoCategoria::ativa)
                .orElseThrow(CategoriaIndisponivelException::new);
    }
}
