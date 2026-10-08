package com.limio.api.comum.seguranca;

import java.util.UUID;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

/**
 * Quem está logado, do jeito que qualquer módulo enxerga: id + papel ativo,
 * lidos do token de acesso pelo {@link JwtAuthFilter}. Controller recebe via
 * {@code @AuthenticationPrincipal} — nunca importa {@code Usuario} de
 * {@code modulos.auth}.
 *
 * O papel é o do momento em que o token foi emitido: depois de uma troca de
 * papel, só o próximo token (refresh ou login) traz o papel novo.
 */
public record UsuarioAutenticado(UUID id, PapelUsuario papel) {
}
