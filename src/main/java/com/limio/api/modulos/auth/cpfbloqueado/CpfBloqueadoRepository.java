package com.limio.api.modulos.auth.cpfbloqueado;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface CpfBloqueadoRepository extends BaseRepository<CpfBloqueado, UUID> {

    boolean existsByCpfHmacAndBloqueadoEmAfterAndLiberadoEmIsNull(String cpfHmac, Instant limite);
}
