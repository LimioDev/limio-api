package com.limio.api.modulos.financeiro.assinaturagold;

import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.financeiro.assinaturagold.enums.StatusAssinatura;

public interface AssinaturaGoldRepository extends BaseRepository<AssinaturaGold, UUID> {

    Optional<AssinaturaGold> findByUsuarioId(UUID usuarioId);

    long countByStatus(StatusAssinatura status);
}
