package com.limio.api.modulos.auth.tentativalogin;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

public interface TentativaLoginRepository extends BaseRepository<TentativaLogin, UUID> {

    List<TentativaLogin> findByEmailOrderByCriadoEmDesc(String email, Limit limite);

    /** Lock do Postgres por e-mail, solto no fim da transação de quem chama. */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtext(:email))", nativeQuery = true)
    Integer travarPorEmail(@Param("email") String email);
}
