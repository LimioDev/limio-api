package com.limio.api.comum.seguranca;

import java.time.Instant;

/** Token de acesso já conferido: quem é o dono e até quando vale. */
public record TokenAcesso(UsuarioAutenticado usuario, Instant expiraEm) {
}
