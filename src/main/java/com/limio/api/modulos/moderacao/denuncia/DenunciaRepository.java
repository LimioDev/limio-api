package com.limio.api.modulos.moderacao.denuncia;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.moderacao.enums.StatusResolucao;

public interface DenunciaRepository extends BaseRepository<Denuncia, UUID> {

    List<Denuncia> findByDenuncianteIdAndDenunciadoIdAndStatusResolucaoIn(UUID denuncianteId, UUID denunciadoId,
            Collection<StatusResolucao> status);
}
