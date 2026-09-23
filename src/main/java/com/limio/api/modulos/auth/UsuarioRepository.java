package com.limio.api.modulos.auth;

import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface UsuarioRepository extends BaseRepository<Usuario, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByCpf(String cpf);
}
