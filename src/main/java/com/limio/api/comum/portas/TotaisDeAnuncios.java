package com.limio.api.comum.portas;

/**
 * Totais do {@code anuncio} desde o início da plataforma, qualquer que seja o status atual.
 *
 * @param anuncios     todos os anúncios criados
 * @param candidaturas todas as candidaturas enviadas
 * @param escolhas     candidaturas em que o Empregador escolheu o Prestador, mesmo que depois tenham sido encerradas
 */
public record TotaisDeAnuncios(long anuncios, long candidaturas, long escolhas) {
}
