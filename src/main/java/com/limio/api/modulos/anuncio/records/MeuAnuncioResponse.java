package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

/**
 * Resposta de {@code GET /anuncios/{id}} (tela 16, ETI-59), só pro dono. {@code candidatos} conta as
 * candidaturas {@code ENVIADA}; {@code primeirosCandidatos} traz as mais antigas delas. Latitude e longitude
 * não saem nem aqui.
 */
public record MeuAnuncioResponse(
        UUID id,
        StatusAnuncio status,
        String descricao,
        CategoriaAnuncioResponse categoria,
        Instant dataHoraPretendida,
        String bairroRegiao,
        Uf uf,
        String enderecoCompleto,
        String tamanhoLocal,
        Integer numeroPessoas,
        String observacoes,
        List<FotoAnuncioResponse> fotos,
        int candidatos,
        List<CandidatoResumidoResponse> primeirosCandidatos) {
}
