package com.limio.api.modulos.perfil.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.perfil.empregador.MeuPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.records.PerfilEmpregadorResponse;

@Component
public class PerfilEmpregadorMapper {

    public PerfilEmpregadorResponse toResponse(MeuPerfilEmpregador meuPerfil) {
        PerfilEmpregador perfil = meuPerfil.perfil();
        return new PerfilEmpregadorResponse(
                perfil.getNomeExibicao(),
                perfil.getMediaAvaliacoes(),
                perfil.getQtdAvaliacoes(),
                meuPerfil.dados().email(),
                meuPerfil.dados().telefone(),
                meuPerfil.dados().emailConfirmadoEm(),
                meuPerfil.dados().telefoneConfirmadoEm());
    }
}
