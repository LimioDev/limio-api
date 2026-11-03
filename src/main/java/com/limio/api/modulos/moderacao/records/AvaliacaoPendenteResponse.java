package com.limio.api.modulos.moderacao.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

/**
 * Resposta de {@code GET /anuncios/{anuncioId}/avaliacao-pendente} (tela 45). {@code servico.concluidoEm} e
 * {@code avaliarAte} são {@code null} enquanto o serviço não foi concluído. {@code podeAvaliar} é {@code false} antes da
 * conclusão, depois de {@code avaliarAte} e depois que quem consulta já avaliou.
 */
public record AvaliacaoPendenteResponse(
        Servico servico,
        OutraParte outraParte,
        boolean podeAvaliar,
        Instant avaliarAte) {

    public record Servico(UUID id, String titulo, Instant concluidoEm) {
    }

    /** {@code nome} é "Usuário excluído", sem foto, quando a conta foi encerrada. */
    public record OutraParte(String nome, String fotoUrl, Instant fotoExpiraEm, PapelUsuario papel) {
    }
}
