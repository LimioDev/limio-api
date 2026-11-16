package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O Empregador validou o serviço, presencial ou pela foto (ETI-92): o anúncio foi pra {@code CONCLUIDO}.
 * Único consumidor previsto, ainda não em código: {@code notificacao} avisa o Prestador (ETI-112).
 * {@code moderacao} (ETI-107, já implementado) não escuta este evento — lê {@code validadoEm} direto pela
 * porta {@code ConsultaServico}/{@code ResumoServico}. {@code anuncioTitulo} vai solto pelo mesmo motivo de
 * {@link ServicoIniciadoEvent}: quem escuta não pode importar {@code modulos.anuncio}.
 */
public record ServicoConcluidoEvent(
        UUID anuncioId,
        String anuncioTitulo,
        UUID contratanteId,
        UUID prestadorId,
        Instant ocorridoEm) {
}
