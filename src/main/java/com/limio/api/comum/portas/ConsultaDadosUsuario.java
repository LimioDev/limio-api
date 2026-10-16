package com.limio.api.comum.portas;

import java.util.Optional;
import java.util.UUID;

public interface ConsultaDadosUsuario {

    Optional<DadosUsuario> buscarPorId(UUID usuarioId);

}
