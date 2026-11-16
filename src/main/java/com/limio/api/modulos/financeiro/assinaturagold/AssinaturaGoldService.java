package com.limio.api.modulos.financeiro.assinaturagold;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link AssinaturaGold} — CRUD genérico (herdado) + finder da entity. */
@Component
public class AssinaturaGoldService extends BaseService<AssinaturaGold, UUID, AssinaturaGoldRepository> {

    public AssinaturaGoldService(AssinaturaGoldRepository repository) {
        super(repository);
    }

    public Optional<AssinaturaGold> buscarPorUsuario(UUID usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }
}
