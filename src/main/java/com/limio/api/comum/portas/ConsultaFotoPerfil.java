package com.limio.api.comum.portas;

import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.midia.LinkTemporario;

/**
 * Porta de leitura da foto de perfil de qualquer usuário, para módulos que não podem importar {@code modulos.perfil}
 * (o cabeçalho do chat e os candidatos do anúncio). Só consulta; quem implementa é {@code perfil}, que também usa
 * a mesma implementação no resumo do Empregador: buscar a foto ativa, gerar o link e tolerar o storage fora do ar fica
 * num lugar só.
 *
 * <p>Vazio quando o usuário não tem foto ou o link não pôde ser gerado agora: a foto nunca derruba a consulta.
 */
public interface ConsultaFotoPerfil {

    /**
     * @param usuarioId id do usuário
     * @return link temporário de leitura da foto atual
     */
    Optional<LinkTemporario> buscarFoto(UUID usuarioId);

}
