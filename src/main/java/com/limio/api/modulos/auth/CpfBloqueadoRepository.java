package com.limio.api.modulos.auth;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface CpfBloqueadoRepository extends BaseRepository<CpfBloqueado, UUID> {

    boolean existsByCpfAndCriadoEmAfter(String cpf, Instant limite);
}
