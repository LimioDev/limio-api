package com.limio.api.modulos.perfil.empregador;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;

@Component
public class PerfilEmpregadorService extends BaseService<PerfilEmpregador, UUID, PerfilEmpregadorRepository> {

    public PerfilEmpregadorService(PerfilEmpregadorRepository repository) {
        super(repository);
    }

    public PerfilEmpregador buscarPorUsuarioIdOuFalhar(UUID usuarioId) {
        return repository.findByUsuarioId(usuarioId).orElseThrow(PerfilEmpregadorNaoEncontradoException::new);
    }
}
