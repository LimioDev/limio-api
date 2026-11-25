package com.limio.api.comum.portas;

/**
 * Porta de leitura dos totais de anúncios, candidaturas e escolhas para o dashboard do administrador
 * ({@code modulos.administracao}, ETI-117). Só consulta; quem implementa é o {@code anuncio}.
 */
public interface ConsultaTotaisDeAnuncios {

    TotaisDeAnuncios contar();
}
