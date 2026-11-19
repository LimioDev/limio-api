package com.limio.api.modulos.anuncio.reagendamento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;

public interface ReagendamentoRepository extends BaseRepository<Reagendamento, UUID> {

    List<Reagendamento> findByAnuncioIdInAndStatus(List<UUID> anuncioIds, StatusReagendamento status);

    Optional<Reagendamento> findFirstByAnuncioIdAndStatusOrderByRespondidoEmDesc(UUID anuncioId,
            StatusReagendamento status);
}
