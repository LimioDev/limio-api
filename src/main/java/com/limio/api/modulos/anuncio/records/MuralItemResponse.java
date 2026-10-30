package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.UUID;

/**
 * Item do mural. De propósito não tem endereço, telefone, e-mail, WhatsApp nem coordenadas: localização é
 * só {@code bairroRegiao} + {@code distanciaKm} (arredondada a 0,1 km). {@code fotoUrl} é {@code null} sem foto.
 */
public record MuralItemResponse(
        UUID id,
        String titulo,
        CategoriaAnuncioResponse categoria,
        String fotoUrl,
        EmpregadorNoMuralResponse empregador,
        Instant dataHoraPretendida,
        String bairroRegiao,
        double distanciaKm) {
}
