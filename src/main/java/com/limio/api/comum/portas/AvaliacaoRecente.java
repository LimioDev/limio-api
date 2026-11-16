package com.limio.api.comum.portas;

import java.time.Instant;

/**
 * Uma avaliação recebida, pro card de reputação do perfil público (ETI-105). {@code avaliadorNome} já vem
 * abreviado ("Camila R.") — quem consulta não recebe o nome completo de quem avaliou. Quando a moderação
 * de avaliações existir (sem card ainda, sem campo {@code ocultaEm} na entity hoje), uma avaliação
 * ocultada não deve ser devolvida por {@link ConsultaAvaliacoes#listarRecentes}.
 */
public record AvaliacaoRecente(String avaliadorNome, int nota, String comentario, Instant data) {
}
