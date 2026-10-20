package com.limio.api.modulos.auth.cpfbloqueado;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

public interface CpfBloqueadoRepository extends BaseRepository<CpfBloqueado, UUID> {

    boolean existsByCpfHmacAndLiberadoEmAfter(String cpfHmac, Instant agora);

    @Modifying
    @Query("delete from CpfBloqueado c where c.liberadoEm < :limite")
    void apagarLiberadosAntesDe(@Param("limite") Instant limite);
}
