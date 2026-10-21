package com.limio.api.modulos.perfil.records;

import java.util.UUID;

/** {@code grupo} é o cabeçalho sob o qual a tela "Especialidades" mostra a categoria. */
public record CategoriaResponse(UUID id, String nome, String grupo) {
}
