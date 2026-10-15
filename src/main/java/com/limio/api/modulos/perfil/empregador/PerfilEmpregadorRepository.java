package com.limio.api.modulos.perfil.empregador;

import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface PerfilEmpregadorRepository extends BaseRepository<PerfilEmpregador, UUID> {

    Optional<PerfilEmpregador> findByUsuarioId(UUID usuarioId);
}
