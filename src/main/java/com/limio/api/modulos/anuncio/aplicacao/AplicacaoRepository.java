package com.limio.api.modulos.anuncio.aplicacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

public interface AplicacaoRepository extends BaseRepository<Aplicacao, UUID> {

    List<Aplicacao> findByPrestadorIdAndStatusIn(UUID prestadorId, List<StatusAplicacao> status);

    Optional<Aplicacao> findByAnuncioIdAndStatus(UUID anuncioId, StatusAplicacao status);
}
