package com.limio.api.modulos.auth.verificacaocontato;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

public interface VerificacaoContatoRepository extends BaseRepository<VerificacaoContato, UUID> {

    Optional<VerificacaoContato> findByTokenHashAndFinalidade(String tokenHash, FinalidadeVerificacao finalidade);

    long countByUsuarioIdAndFinalidadeAndCriadoEmAfter(UUID usuarioId, FinalidadeVerificacao finalidade, Instant desde);
}
