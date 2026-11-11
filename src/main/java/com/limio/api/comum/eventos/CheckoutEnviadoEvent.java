package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O Prestador escolhido fez o check-out com fotos e o serviço passou a aguardar a validação do Empregador
 * (ETI-90). Consumidor previsto, ainda não em código: {@code notificacao} avisa o Empregador (ETI-112).
 * {@code anuncioTitulo} vai solto pelo mesmo motivo de {@link ServicoIniciadoEvent}: quem escuta não pode
 * importar {@code modulos.anuncio} (ADR-0001).
 */
public record CheckoutEnviadoEvent(
        UUID anuncioId,
        String anuncioTitulo,
        UUID contratanteId,
        UUID prestadorId,
        Instant ocorridoEm) {
}
