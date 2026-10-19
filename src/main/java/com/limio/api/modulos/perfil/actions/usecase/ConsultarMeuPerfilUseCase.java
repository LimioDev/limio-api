package com.limio.api.modulos.perfil.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.perfil.empregador.MeuPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;

@Service
public class ConsultarMeuPerfilUseCase {

    private final PerfilEmpregadorService perfilEmpregadorService;

    public ConsultarMeuPerfilUseCase(PerfilEmpregadorService perfilEmpregadorService) {
        this.perfilEmpregadorService = perfilEmpregadorService;
    }

    @Transactional
    public MeuPerfilEmpregador executar(UUID usuarioId) {
        return perfilEmpregadorService.garantirPerfil(usuarioId);
    }
}
