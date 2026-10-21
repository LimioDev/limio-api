package com.limio.api.modulos.perfil.categoria;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface CategoriaRepository extends BaseRepository<Categoria, UUID> {

    List<Categoria> findByAtivaTrueOrderByOrdemAscNomeAsc();

    List<Categoria> findByIdInAndAtivaTrue(Collection<UUID> ids);
}
