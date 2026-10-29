package com.limio.api.comum.portas;

import java.util.UUID;

public record ResumoCategoria(UUID id, String nome, String grupo, boolean ativa) {
}
