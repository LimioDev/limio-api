package com.limio.api.comum.portas;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura da foto de perfil de qualquer usuário, para módulos que não podem importar {@code modulos.perfil}
 * (hoje o cabeçalho do chat, ETI-101). Só consulta; quem implementa é {@code perfil}.
 *
 * <p>Vazio quando o usuário não tem foto ou o link não pôde ser gerado agora: a foto nunca derruba a consulta.
 */
public interface ConsultaFotoPerfil {

    /**
     * @param usuarioId id do usuário
     * @return link temporário de leitura da foto atual
     */
    Optional<FotoPerfil> buscarFoto(UUID usuarioId);

}
