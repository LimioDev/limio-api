package com.limio.api.modulos.moderacao.avaliacao;

import java.util.UUID;

/** Linha de {@code AvaliacaoRepository.resumirPorAvaliados} — quantidade e média de um avaliado, direto do banco. */
public interface ResumoAvaliacaoProjecao {

    UUID getAvaliadoId();

    Double getMedia();

    int getQtd();
}
