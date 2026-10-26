package com.limio.api.modulos.auth.tentativalogin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

public interface TentativaLoginRepository extends BaseRepository<TentativaLogin, UUID> {

    List<TentativaLogin> findByEmailOrderByCriadoEmDesc(String email, Limit limite);

    List<TentativaLogin> findByIpOrderByCriadoEmDesc(String ip, Limit limite);

    @Modifying
    @Query("delete from TentativaLogin t where t.criadoEm < :limite")
    int apagarAntesDe(@Param("limite") Instant limite);
}
