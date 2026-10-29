package com.limio.api.modulos.anuncio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

public record DetalheDoMural(UUID id, String titulo, String descricao, ResumoCategoria categoria,
        List<FotoDoAnuncio> fotos, Instant dataHoraPretendida, String bairroRegiao, String cidade, Uf uf,
        double distanciaKm, ResumoPerfilEmpregador empregador, StatusAplicacao minhaCandidatura) {
}
