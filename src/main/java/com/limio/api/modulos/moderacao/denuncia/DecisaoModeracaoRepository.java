package com.limio.api.modulos.moderacao.denuncia;

import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface DecisaoModeracaoRepository extends BaseRepository<DecisaoModeracao, UUID> {

    Optional<DecisaoModeracao> findByDenunciaId(UUID denunciaId);
}
