package com.limio.api.modulos.anuncio;

import java.time.Instant;
import java.util.UUID;

/**
 * Linha do mural direto do banco — só o que a tela mostra. Sem endereço completo, sem coordenadas do
 * anúncio: a distância já vem calculada e a localização é só o bairro/região.
 */
public interface AnuncioDoMuralProjecao {

    UUID getId();

    String getTitulo();

    UUID getCategoriaId();

    UUID getContratanteId();

    Instant getDataHoraPretendida();

    String getBairroRegiao();

    double getDistanciaKm();
}
