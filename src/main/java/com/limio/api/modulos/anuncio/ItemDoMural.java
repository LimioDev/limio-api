package com.limio.api.modulos.anuncio;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;

/**
 * Um anúncio como o Prestador o vê no mural — nada de contato nem endereço. {@code primeiraFoto} é
 * {@code null} sem foto ou com o storage fora do ar.
 */
public record ItemDoMural(UUID id, String titulo, ResumoCategoria categoria, LinkTemporario primeiraFoto,
        ResumoPerfilEmpregador empregador, Instant dataHoraPretendida, String bairroRegiao, double distanciaKm) {
}
