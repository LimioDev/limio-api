package com.limio.api.comum.portas;

import java.time.Instant;

/** {@code fotoUrl} é um link temporário de leitura, válido até {@code fotoExpiraEm}; ambos {@code null} sem foto. */
public record ResumoPerfilEmpregador(String nomeExibicao, String fotoUrl, Instant fotoExpiraEm) {
}
