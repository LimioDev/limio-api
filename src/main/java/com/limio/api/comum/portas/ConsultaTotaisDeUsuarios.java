package com.limio.api.comum.portas;

/**
 * Porta de leitura do total de usuários para o dashboard do administrador ({@code modulos.administracao}, ETI-117).
 * Só consulta; quem implementa é o {@code auth}.
 */
public interface ConsultaTotaisDeUsuarios {

    /** Contas que ainda existem na plataforma: tudo menos as encerradas (anonimizadas), qualquer que seja o status. */
    long contarContasNaoEncerradas();
}
