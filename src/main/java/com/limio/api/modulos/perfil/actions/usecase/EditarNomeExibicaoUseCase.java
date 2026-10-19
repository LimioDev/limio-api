package com.limio.api.modulos.perfil.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.perfil.empregador.MeuPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;

@Service
public class EditarNomeExibicaoUseCase {

    private final PerfilEmpregadorService perfilEmpregadorService;

    public EditarNomeExibicaoUseCase(PerfilEmpregadorService perfilEmpregadorService) {
        this.perfilEmpregadorService = perfilEmpregadorService;
    }

    @Transactional
    public MeuPerfilEmpregador executar(UUID usuarioId, String nomeExibicao) {
        MeuPerfilEmpregador atual = perfilEmpregadorService.garantirPerfil(usuarioId);
        PerfilEmpregador perfil = atual.perfil();
        perfil.setNomeExibicao(nomeExibicao);
        return new MeuPerfilEmpregador(perfilEmpregadorService.salvar(perfil), atual.dados());
    }
}
