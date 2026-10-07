package com.limio.api.modulos.auth.sessao;

import java.time.Instant;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

/**
 * Resultado de abrir ou renovar uma {@link Sessao}: o que o app precisa
 * guardar. É o único lugar em que o refresh token existe em texto puro — no
 * banco fica só o hash.
 */
public record TokensSessao(String tokenAcesso, Instant tokenAcessoExpiraEm, String refreshToken,
        Instant refreshTokenExpiraEm, PapelUsuario papel) {
}
