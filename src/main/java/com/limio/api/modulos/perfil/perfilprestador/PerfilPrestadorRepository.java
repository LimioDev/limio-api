package com.limio.api.modulos.perfil.perfilprestador;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Em lote, com as especialidades no fetch. */
    @EntityGraph(attributePaths = "especialidades")
    List<PerfilPrestador> findByUsuarioIdIn(Collection<UUID> usuarioIds);

    Optional<PerfilPrestador> findFirstByUfBaseAndMunicipioBaseIgnoreCase(Uf ufBase, String municipioBase);

    /**
     * Prestadores que atendem um anúncio (ETI-65), com a distância (haversine, km) da base até ele. Mesma regra do
     * mural ({@code AnuncioRepository.MURAL_CANDIDATOS}), do outro lado: perfil completo; categoria do anúncio nas
     * especialidades, ou o anúncio é "Outros" ({@code categoria.protegida}), ou o Prestador tem "Outros"; distância
     * menor ou igual ao raio de cada um. Sem caixa envolvente: o raio muda por Prestador. Ordem: mais perto, depois
     * usuário (estável).
     */
    @Query(value = """
            SELECT m.usuario_id AS "usuarioId", m.distancia_km AS "distanciaKm"
            FROM (
                SELECT p.usuario_id, p.raio_atendimento_km,
                       2 * 6371.0 * asin(sqrt(least(1.0,
                           power(sin(radians(p.latitude_base - :latitude) / 2), 2)
                           + cos(radians(:latitude)) * cos(radians(p.latitude_base))
                             * power(sin(radians(p.longitude_base - :longitude) / 2), 2)))) AS distancia_km
                FROM perfil_prestador p
                WHERE p.completo
                  AND (EXISTS (SELECT 1 FROM categoria c WHERE c.id = :categoriaId AND c.protegida)
                       OR EXISTS (SELECT 1 FROM prestador_categoria pc JOIN categoria c ON c.id = pc.categoria_id
                                  WHERE pc.perfil_prestador_id = p.id
                                    AND (pc.categoria_id = :categoriaId OR c.protegida)))
            ) m
            WHERE m.distancia_km <= m.raio_atendimento_km
            ORDER BY m.distancia_km, m.usuario_id
            """, nativeQuery = true)
    List<PrestadorCompativelProjecao> buscarCompativeis(@Param("categoriaId") UUID categoriaId,
            @Param("latitude") double latitude, @Param("longitude") double longitude);

    /** Só o id, sem carregar especialidades nem janelas. */
    @Query("select p.id from PerfilPrestador p where p.usuarioId = :usuarioId")
    Optional<UUID> findIdByUsuarioId(UUID usuarioId);
}
