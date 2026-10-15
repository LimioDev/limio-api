package com.limio.api.modulos.perfil.perfilprestador;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.perfil.perfilprestador.enums.Uf;

public interface PerfilPrestadorRepository extends BaseRepository<PerfilPrestador, UUID> {

    /** Já traz especialidades e janelas: o mapper lê as duas no controller, fora da transação. */
    @EntityGraph(attributePaths = { "especialidades", "janelas" })
    Optional<PerfilPrestador> findByUsuarioId(UUID usuarioId);

    Optional<PerfilPrestador> findFirstByUfBaseAndMunicipioBaseIgnoreCase(Uf ufBase, String municipioBase);

    /**
     * Lock do Postgres por usuário, solto no fim da transação de quem chama. Forma de duas chaves
     * (namespace {@code 'perfil_prestador'} + usuário): não colide com o lock de uma chave do login
     * ({@code TentativaLoginRepository.travarPorEmail}).
     */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtext('perfil_prestador'), hashtext(:usuarioId))",
            nativeQuery = true)
    Integer travarPorUsuario(@Param("usuarioId") String usuarioId);
}
