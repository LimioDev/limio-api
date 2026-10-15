package com.limio.api.modulos.perfil.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;

/** Grava o nome de exibição do perfil do próprio usuário. Validação de formato fica no record. */
@Service
public class EditarNomeExibicaoUseCase {

    private final PerfilEmpregadorService perfilEmpregadorService;

    public EditarNomeExibicaoUseCase(PerfilEmpregadorService perfilEmpregadorService) {
        this.perfilEmpregadorService = perfilEmpregadorService;
    }

    @Transactional
    public PerfilEmpregador executar(UUID usuarioId, String nomeExibicao) {
        PerfilEmpregador perfil = perfilEmpregadorService.buscarPorUsuarioIdOuFalhar(usuarioId);
        perfil.setNomeExibicao(nomeExibicao);
        return perfilEmpregadorService.salvar(perfil);
    }
}
