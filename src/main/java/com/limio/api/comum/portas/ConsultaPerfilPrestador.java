package com.limio.api.comum.portas;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura da atuação do Prestador (especialidades, raio e base) para módulos que não podem
 * importar {@code modulos.perfil} (hoje o {@code anuncio}, mural da ETI-73). Só consulta; quem implementa é
 * o módulo dono do dado.
 *
 * <p>Perfil que o Prestador nunca salvou e perfil incompleto são o mesmo caso para quem consulta: vazio.
 * O critério de "completo" é do módulo dono do dado e pode mudar sem afetar quem consome a porta.
 */
public interface ConsultaPerfilPrestador {

    /**
     * @param usuarioId id do usuário (Prestador)
     * @return a atuação, ou vazio se o usuário não tem perfil de atuação completo
     */
    Optional<ResumoPerfilPrestador> buscarCompleto(UUID usuarioId);
}
