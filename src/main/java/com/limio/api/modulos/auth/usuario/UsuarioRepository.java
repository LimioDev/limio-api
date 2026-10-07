package com.limio.api.modulos.auth.usuario;

import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface UsuarioRepository extends BaseRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByCpf(String cpf);
}
