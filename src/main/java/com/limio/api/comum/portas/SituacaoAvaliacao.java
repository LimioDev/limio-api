package com.limio.api.comum.portas;

/**
 * Situação de um usuário na avaliação de um serviço concluído (ETI-71, histórico).
 *
 * @param notaRecebida nota (1 a 5) que a outra parte deu a este usuário no serviço; {@code null} se ela não avaliou
 *        (a avaliação é opcional, RF55)
 * @param podeAvaliar o usuário ainda pode avaliar a outra parte: janela de 7 dias aberta e ainda sem avaliação dele
 */
public record SituacaoAvaliacao(Integer notaRecebida, boolean podeAvaliar) {
}
