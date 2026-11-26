package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.eventos.enums.OrigemConclusao;

/**
 * O anúncio foi pra {@code CONCLUIDO}; {@code origem} diz se por validação do Empregador (ETI-92) ou por silêncio
 * (ETI-98). {@code anuncioTitulo} vai solto pelo mesmo motivo de {@link ServicoIniciadoEvent}: quem escuta não pode
 * importar {@code modulos.anuncio}.
 */
public record ServicoConcluidoEvent(
        UUID anuncioId,
        String anuncioTitulo,
        UUID contratanteId,
        UUID prestadorId,
        OrigemConclusao origem,
        Instant ocorridoEm) {
}
