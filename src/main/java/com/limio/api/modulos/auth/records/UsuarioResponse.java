package com.limio.api.modulos.auth.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.auth.enums.PapelUsuario;

public record UsuarioResponse(
        UUID id,
        String nomeCompleto,
        String email,
        PapelUsuario papelAtivo,
        Instant criadoEm) {
}
