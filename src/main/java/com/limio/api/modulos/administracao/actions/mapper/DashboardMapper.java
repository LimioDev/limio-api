package com.limio.api.modulos.administracao.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.administracao.NumerosDaOperacao;
import com.limio.api.modulos.administracao.records.DashboardResponse;

@Component
public class DashboardMapper {

    public DashboardResponse toResponse(NumerosDaOperacao numeros) {
        return new DashboardResponse(
                numeros.usuarios(),
                numeros.anuncios(),
                numeros.candidaturas(),
                numeros.escolhas(),
                numeros.assinaturasGoldAtivas(),
                numeros.denunciasAbertas(),
                numeros.statusOperacional(),
                numeros.statusOperacional().descricao());
    }
}
