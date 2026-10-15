package com.limio.api.modulos.perfil.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.records.PerfilEmpregadorResponse;

/**
 * Anti-corruption layer entre {@link PerfilEmpregador} e os records HTTP. Chamado só pelo controller.
 * Não implementa {@code BaseMapper}: nenhum request cria a entity (o perfil só é editado).
 */
@Component
public class PerfilEmpregadorMapper {

    public PerfilEmpregadorResponse toResponse(PerfilEmpregador entity) {
        return new PerfilEmpregadorResponse(
                entity.getNomeExibicao(),
                entity.getMediaAvaliacoes(),
                entity.getQtdAvaliacoes());
    }
}
