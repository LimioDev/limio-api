package com.limio.api.modulos.financeiro.assinaturagold;

import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface AssinaturaGoldRepository extends BaseRepository<AssinaturaGold, UUID> {

    Optional<AssinaturaGold> findByUsuarioId(UUID usuarioId);
}
