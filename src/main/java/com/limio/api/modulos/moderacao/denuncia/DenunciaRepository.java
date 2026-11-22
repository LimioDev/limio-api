package com.limio.api.modulos.moderacao.denuncia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;
import com.limio.api.modulos.moderacao.enums.StatusResolucao;

import jakarta.persistence.LockModeType;

public interface DenunciaRepository extends BaseRepository<Denuncia, UUID> {

    long countByStatusResolucao(StatusResolucao statusResolucao);

    /** Fila do admin (ETI-116): a ordem vem do {@code Pageable} (prazo mais curto primeiro). */
    Page<Denuncia> findByStatusResolucaoAndMotivoIn(StatusResolucao statusResolucao, Collection<MotivoDenuncia> motivos,
            Pageable pageable);

    /** Trava a denúncia: dois administradores decidindo ao mesmo tempo não gravam duas decisões. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Denuncia d where d.id = :id")
    Optional<Denuncia> findByIdParaDecidir(@Param("id") UUID id);

    /** Outras denúncias contra o mesmo usuário, da mais recente para a mais antiga. */
    List<Denuncia> findByDenunciadoIdAndIdNotOrderByCriadoEmDesc(UUID denunciadoId, UUID id);
}
