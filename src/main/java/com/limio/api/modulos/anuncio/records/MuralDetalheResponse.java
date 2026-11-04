package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.limio.api.comum.localizacao.enums.Uf;

/**
 * Detalhe do anúncio no mural. De propósito não tem endereço completo, logradouro, telefone, e-mail, WhatsApp
 * nem coordenadas: localização é {@code bairroRegiao}/{@code cidade}/{@code uf} + {@code distanciaKm}
 * (arredondada a 0,1 km). {@code cidade} é {@code null} em anúncio antigo cujo endereço não pôde ser lido.
 * {@code minhaCandidatura} ({@code id} + {@code status}) é {@code null} se o Prestador ainda não se candidatou: aí a
 * tela mostra "Candidatar-se"; o {@code id} é o da candidatura, que o app usa para abrir o chat dela.
 */
public record MuralDetalheResponse(
        UUID id,
        String titulo,
        String descricao,
        CategoriaAnuncioResponse categoria,
        List<FotoAnuncioResponse> fotos,
        Instant dataHoraPretendida,
        String bairroRegiao,
        String cidade,
        Uf uf,
        double distanciaKm,
        EmpregadorNoDetalheResponse empregador,
        CandidaturaResponse minhaCandidatura) {
}
