package com.limio.api.modulos.auth.verificacaocontato;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

import jakarta.persistence.LockModeType;

public interface VerificacaoContatoRepository extends BaseRepository<VerificacaoContato, UUID> {

    Optional<VerificacaoContato> findByTokenHashAndFinalidade(String tokenHash, FinalidadeVerificacao finalidade);

    /** Só o id do dono — sem carregar o token, que precisa ser lido de novo (travado) depois do lock da conta. */
    @Query("select v.usuario.id from VerificacaoContato v where v.tokenHash = :tokenHash and v.finalidade = :finalidade")
    Optional<UUID> findUsuarioIdByTokenHashAndFinalidade(@Param("tokenHash") String tokenHash,
            @Param("finalidade") FinalidadeVerificacao finalidade);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VerificacaoContato v where v.tokenHash = :tokenHash and v.finalidade = :finalidade")
    Optional<VerificacaoContato> findParaConsumir(@Param("tokenHash") String tokenHash,
            @Param("finalidade") FinalidadeVerificacao finalidade);

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from VerificacaoContato v where v.usuario.id = :usuarioId")
    int apagarDoUsuario(@Param("usuarioId") UUID usuarioId);

    long countByUsuarioIdAndFinalidadeAndCriadoEmAfter(UUID usuarioId, FinalidadeVerificacao finalidade, Instant desde);

    List<VerificacaoContato> findByUsuarioIdAndFinalidadeAndConsumidoEmIsNull(UUID usuarioId,
            FinalidadeVerificacao finalidade);
}
