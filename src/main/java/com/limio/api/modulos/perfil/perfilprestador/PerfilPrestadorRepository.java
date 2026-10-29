package com.limio.api.modulos.perfil.perfilprestador;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.comum.localizacao.enums.Uf;

public interface PerfilPrestadorRepository extends BaseRepository<PerfilPrestador, UUID> {

    /**
     * Já traz as especialidades. As janelas não entram no mesmo fetch: dois joins de coleção na mesma query
     * viram produto cartesiano, e a {@code List} de janelas voltava repetida (2 especialidades x 2 janelas =
     * 4 janelas). {@link PerfilPrestadorService#buscarPorUsuario} carrega as janelas numa segunda query.
     */
    @EntityGraph(attributePaths = "especialidades")
    Optional<PerfilPrestador> findByUsuarioId(UUID usuarioId);

    Optional<PerfilPrestador> findFirstByUfBaseAndMunicipioBaseIgnoreCase(Uf ufBase, String municipioBase);
}
