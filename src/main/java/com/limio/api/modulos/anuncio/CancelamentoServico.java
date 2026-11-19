package com.limio.api.modulos.anuncio;

/**
 * Resultado do cancelamento do serviço combinado pelo Empregador (ETI-94): o {@code Anuncio} já encerrado (com o
 * {@code tipoCancelamento}) e se a cota FREE do Prestador foi estornada nesta operação.
 */
public record CancelamentoServico(Anuncio anuncio, boolean cotaEstornada) {
}
