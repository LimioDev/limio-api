package com.limio.api.comum.portas;

/**
 * Porta de leitura do total de denúncias para o dashboard do administrador ({@code modulos.administracao}, ETI-117).
 * Só consulta; quem implementa é o {@code moderacao}.
 */
public interface ConsultaTotaisDeDenuncias {

    /** Denúncias ainda na fila do admin: o mesmo número do título da fila de denúncias. */
    long contarAbertas();
}
