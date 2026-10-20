package com.limio.api.comum.auditoria;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

public interface LogAuditoriaRepository extends BaseRepository<LogAuditoria, UUID> {

    @Modifying
    @Query("delete from LogAuditoria l where l.criadoEm < :limite")
    void apagarAntesDe(@Param("limite") Instant limite);
}
