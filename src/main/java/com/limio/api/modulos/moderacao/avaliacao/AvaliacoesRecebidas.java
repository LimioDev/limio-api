package com.limio.api.modulos.moderacao.avaliacao;

import java.math.BigDecimal;

/** Total de avaliações de um usuário num papel. {@code media} é {@code null} com {@code qtd == 0} (RB11, nunca 0,0). */
public record AvaliacoesRecebidas(BigDecimal media, int qtd) {
}
