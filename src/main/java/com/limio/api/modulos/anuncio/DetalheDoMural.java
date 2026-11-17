package com.limio.api.modulos.anuncio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;

/**
 * {@code empregadorId} é o usuário dono do anúncio, pro app abrir o perfil público (ETI-136). Fica fora de
 * {@link ResumoPerfilEmpregador} de propósito: a porta é do módulo {@code perfil}, também usada pelo mural e pelo
 * painel, e o id já vem do anúncio ({@code contratanteId}), sem precisar consultar o perfil.
 */
public record DetalheDoMural(UUID id, String titulo, String descricao, ResumoCategoria categoria,
        List<FotoDoAnuncio> fotos, Instant dataHoraPretendida, String bairroRegiao, String cidade, Uf uf,
        double distanciaKm, UUID empregadorId, ResumoPerfilEmpregador empregador,
        MinhaCandidatura minhaCandidatura) {
}
