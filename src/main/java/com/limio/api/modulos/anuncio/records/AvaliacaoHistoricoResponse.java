package com.limio.api.modulos.anuncio.records;

/**
 * A avaliação do serviço concluído, do ponto de vista de quem consulta (ETI-71). {@code notaRecebida} é {@code null}
 * se a outra parte não avaliou; {@code podeAvaliar} é {@code true} com a janela de 7 dias aberta e sem avaliação dele.
 */
public record AvaliacaoHistoricoResponse(Integer notaRecebida, boolean podeAvaliar) {
}
