package com.limio.api.modulos.financeiro.plano;

import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface PlanoRepository extends BaseRepository<Plano, UUID> {

    Optional<Plano> findByCodigoAndAtivoTrue(String codigo);
}
