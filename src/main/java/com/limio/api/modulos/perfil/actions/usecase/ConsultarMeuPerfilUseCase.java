package com.limio.api.modulos.perfil.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;

/**
 * Consulta o perfil do próprio usuário. O id vem sempre do token (nunca de
 * parâmetro da requisição), então só o dono lê o seu perfil.
 */
@Service
public class ConsultarMeuPerfilUseCase {

    private final PerfilEmpregadorService perfilEmpregadorService;

    public ConsultarMeuPerfilUseCase(PerfilEmpregadorService perfilEmpregadorService) {
        this.perfilEmpregadorService = perfilEmpregadorService;
    }

    @Transactional(readOnly = true)
    public PerfilEmpregador executar(UUID usuarioId) {
        return perfilEmpregadorService.buscarPorUsuarioIdOuFalhar(usuarioId);
    }
}
