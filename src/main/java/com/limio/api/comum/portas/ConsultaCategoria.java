package com.limio.api.comum.portas;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura das categorias de serviço para módulos que não podem importar {@code modulos.perfil}
 * (hoje o {@code anuncio}, ETI-59). Só consulta; quem implementa é o módulo dono do dado.
 *
 * <p>Devolve a categoria mesmo inativa: quem cria algo novo confere {@link ResumoCategoria#ativa()}; quem
 * mostra algo já gravado só precisa do nome.
 */
public interface ConsultaCategoria {

    /**
     * @param categoriaId id da categoria
     * @return a categoria, ou vazio se o id não existe
     */
    Optional<ResumoCategoria> buscarPorId(UUID categoriaId);
}
