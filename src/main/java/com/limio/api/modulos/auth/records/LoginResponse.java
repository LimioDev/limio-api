package com.limio.api.modulos.auth.records;

import java.time.Instant;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

/** Resposta do login e da renovação: mesmo formato, o app guarda os dois tokens do mesmo jeito. */
public record LoginResponse(String token, Instant expiraEm, PapelUsuario papel, String refreshToken) {
}
