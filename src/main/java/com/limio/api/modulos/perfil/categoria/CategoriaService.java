package com.limio.api.modulos.perfil.categoria;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link Categoria} — CRUD genérico (herdado) + finders da entity. */
@Component
public class CategoriaService extends BaseService<Categoria, UUID, CategoriaRepository> {

    public CategoriaService(CategoriaRepository repository) {
        super(repository);
    }

    /** Ativas, em ordem de exibição ({@code ordem}, depois nome). */
    public List<Categoria> listarAtivas() {
        return repository.findByAtivaTrueOrderByOrdemAscNomeAsc();
    }

    /** Só as ativas entre {@code ids} — id inexistente ou de categoria inativa simplesmente não volta. */
    public Set<Categoria> buscarAtivasPorIds(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(repository.findByIdInAndAtivaTrue(ids));
    }

    /** A categoria protegida "Outros" (seed da V11); vazio só se a base foi adulterada. */
    public Optional<Categoria> buscarOutros() {
        return repository.findFirstByProtegidaTrue();
    }

    /** Ativa ou não; vazio só se o id não existe. */
    public Optional<Categoria> buscarPorId(UUID id) {
        return repository.findById(id);
    }
}
