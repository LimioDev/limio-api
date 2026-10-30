package com.limio.api.comum.portas;

import java.time.Instant;
import java.util.UUID;

/**
 * Um serviço em que o Prestador foi escolhido. {@code status} é o nome do status atual da candidatura no
 * {@code anuncio} ({@code ESCOLHIDA}, {@code AGUARDANDO_VALIDACAO}, {@code CONCLUIDA}, {@code ENCERRADA}...),
 * em texto porque o enum é interno daquele módulo.
 */
public record EscolhaDoPrestador(UUID anuncioId, String titulo, Instant dataHoraServico, Instant escolhidaEm,
        String status) {
}
