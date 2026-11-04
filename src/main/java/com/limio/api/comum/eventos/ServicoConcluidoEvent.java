package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O Empregador validou o serviço, presencial ou pela foto (ETI-92): o anúncio foi pra {@code CONCLUIDO}.
 * Consumidores previstos, ainda não em código: {@code moderacao} abre a avaliação das duas partes
 * (ETI-107/ETI-108), {@code notificacao} avisa o Prestador (ETI-112). {@code anuncioTitulo} vai solto pelo
 * mesmo motivo de {@link ServicoIniciadoEvent}: quem escuta não pode importar {@code modulos.anuncio}.
 */
public record ServicoConcluidoEvent(
        UUID anuncioId,
        String anuncioTitulo,
        UUID contratanteId,
        UUID prestadorId,
        Instant ocorridoEm) {
}
