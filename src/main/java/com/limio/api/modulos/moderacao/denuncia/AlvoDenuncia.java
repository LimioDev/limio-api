package com.limio.api.modulos.moderacao.denuncia;

import java.util.UUID;

/**
 * Contra quem é a denúncia e onde o problema aconteceu (ETI-114). {@code denunciadoId} é o usuário denunciado. Os
 * outros três são o contexto, quando há: {@code anuncioId} (o anúncio, ou o serviço quando já tem Prestador
 * escolhido), {@code candidaturaId} (o chat é 1:1 por candidatura) e {@code mensagemId}. Denúncia de perfil só tem o
 * denunciado.
 *
 * <p>Como o cliente informa, cada campo pode vir nulo. Depois de resolvido pelo usecase, {@code denunciadoId} sempre
 * vem preenchido e os contextos de cima também: uma mensagem leva a candidatura e o anúncio dela.
 */
public record AlvoDenuncia(UUID denunciadoId, UUID anuncioId, UUID candidaturaId, UUID mensagemId) {

    public static AlvoDenuncia perfil(UUID denunciadoId) {
        return new AlvoDenuncia(denunciadoId, null, null, null);
    }
}
