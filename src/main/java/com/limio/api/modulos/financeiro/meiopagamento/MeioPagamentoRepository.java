package com.limio.api.modulos.financeiro.meiopagamento;

import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface MeioPagamentoRepository extends BaseRepository<MeioPagamento, UUID> {

    Optional<MeioPagamento> findByUsuarioId(UUID usuarioId);
}
