package com.limio.api.modulos.auth;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link Usuario} — CRUD genérico (herdado) + finders da entity. */
@Component
public class UsuarioService extends BaseService<Usuario, UUID, UsuarioRepository> {

    public UsuarioService(UsuarioRepository repository) {
        super(repository);
    }

    public boolean existePorEmail(String email) {
        return repository.existsByEmailIgnoreCase(email);
    }

    public boolean existePorCpf(String cpf) {
        return repository.existsByCpf(cpf);
    }
}
