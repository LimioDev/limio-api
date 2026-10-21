package com.limio.api.modulos.perfil.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;

/**
 * Regra de negócio do TICKET-0054 (consultar perfil de atuação). Devolve o perfil salvo do usuário ou,
 * se ele nunca salvou, um perfil vazio e incompleto — sem persistir nada. Não conhece HTTP nem record.
 */
@Service
public class ConsultarPerfilAtuacaoUseCase {

    private final PerfilPrestadorService perfilPrestadorService;

    public ConsultarPerfilAtuacaoUseCase(PerfilPrestadorService perfilPrestadorService) {
        this.perfilPrestadorService = perfilPrestadorService;
    }

    public PerfilPrestador executar(UUID usuarioId) {
        return perfilPrestadorService.buscarPorUsuario(usuarioId)
                .orElseGet(() -> PerfilPrestador.novo(usuarioId));
    }
}
