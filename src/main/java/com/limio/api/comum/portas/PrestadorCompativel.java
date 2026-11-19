package com.limio.api.comum.portas;

import java.util.UUID;

/**
 * Prestador que atende um anúncio pela regra do mural (ETI-65): só o id do usuário e a distância em km da base dele
 * até o anúncio. Sem coordenadas de nenhum dos lados.
 */
public record PrestadorCompativel(UUID usuarioId, double distanciaKm) {
}
