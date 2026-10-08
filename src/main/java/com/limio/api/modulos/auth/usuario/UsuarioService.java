package com.limio.api.modulos.auth.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link Usuario} — CRUD genérico (herdado) + finders da entity. */
@Component
public class UsuarioService extends BaseService<Usuario, UUID, UsuarioRepository> {

    public UsuarioService(UsuarioRepository repository) {
        super(repository);
    }

    /** Vazio é fluxo normal no login (e-mail inexistente vira credencial inválida), por isso não lança. */
    public Optional<Usuario> buscarPorEmail(String email) {
        return repository.findByEmailIgnoreCase(email);
    }

    public boolean existePorEmail(String email) {
        return repository.existsByEmailIgnoreCase(email);
    }

    public boolean existePorCpf(String cpf) {
        return repository.existsByCpf(cpf);
    }
}
