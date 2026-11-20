package com.limio.api.modulos.anuncio.records;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * O Empregador no detalhe do mural (tela 30). {@code usuarioId} é o dono do anúncio, o mesmo que {@code GET
 * /perfis-publicos/{usuarioId}} aceita com {@code papel=EMPREGADOR} (ETI-136). Sem telefone, e-mail nem WhatsApp.
 */
public record EmpregadorNoDetalheResponse(UUID usuarioId, String nomeExibicao, String fotoUrl,
        BigDecimal mediaAvaliacoes, int qtdAvaliacoes) {
}
