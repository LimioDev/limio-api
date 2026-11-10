package com.limio.api.comum.portas;

import java.time.Instant;

/**
 * Uma avaliação recebida, pro card de reputação do perfil público (ETI-105). {@code avaliadorNome} já vem
 * abreviado ("Camila R.") — quem consulta não recebe o nome completo de quem avaliou. Avaliação ocultada
 * pela moderação ({@code ocultaEm} preenchido) não é devolvida por {@link ConsultaAvaliacoes#listarRecentes}.
 */
public record AvaliacaoRecente(String avaliadorNome, int nota, String comentario, Instant data) {
}
