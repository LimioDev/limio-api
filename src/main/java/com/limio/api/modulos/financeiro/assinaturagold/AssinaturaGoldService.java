package com.limio.api.modulos.financeiro.assinaturagold;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link AssinaturaGold} — CRUD genérico (herdado) + finders da entity. */
@Component
public class AssinaturaGoldService extends BaseService<AssinaturaGold, UUID, AssinaturaGoldRepository> {

    public AssinaturaGoldService(AssinaturaGoldRepository repository) {
        super(repository);
    }

    public Optional<AssinaturaGold> buscarPorUsuario(UUID usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }

    public Optional<AssinaturaGold> buscarPorId(UUID id) {
        return repository.findById(id);
    }

    public List<UUID> buscarIdsParaRenovar(Instant agora, int lote) {
        return repository.buscarIdsParaRenovar(agora, lote);
    }

    public Optional<UUID> buscarUsuarioId(UUID assinaturaId) {
        return repository.findUsuarioIdById(assinaturaId);
    }
}
