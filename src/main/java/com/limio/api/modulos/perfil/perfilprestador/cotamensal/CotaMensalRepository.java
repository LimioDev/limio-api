package com.limio.api.modulos.perfil.perfilprestador.cotamensal;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface CotaMensalRepository extends BaseRepository<CotaMensal, UUID> {

    Optional<CotaMensal> findByPerfilPrestadorIdAndAnoMes(UUID perfilPrestadorId, YearMonth anoMes);
}
