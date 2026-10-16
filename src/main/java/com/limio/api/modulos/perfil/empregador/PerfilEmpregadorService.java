package com.limio.api.modulos.perfil.empregador;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

@Component
public class PerfilEmpregadorService extends BaseService<PerfilEmpregador, UUID, PerfilEmpregadorRepository> {

    public PerfilEmpregadorService(PerfilEmpregadorRepository repository) {
        super(repository);
    }

    public PerfilEmpregador buscarOuCriar(UUID usuarioId, String nomeInicial) {
        return repository.findByUsuarioId(usuarioId).orElseGet(() -> {
            repository.inserirSeAusente(usuarioId, nomeInicial);
            return repository.findByUsuarioId(usuarioId).orElseThrow();
        });
    }
}
