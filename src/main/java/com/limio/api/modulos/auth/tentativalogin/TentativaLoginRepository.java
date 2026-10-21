package com.limio.api.modulos.auth.tentativalogin;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;

import com.limio.api.comum.base.BaseRepository;

public interface TentativaLoginRepository extends BaseRepository<TentativaLogin, UUID> {

    List<TentativaLogin> findByEmailOrderByCriadoEmDesc(String email, Limit limite);
}
