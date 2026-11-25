package com.limio.api.comum.seguranca;

import java.security.Principal;

/**
 * Quem pode ser dono de um token de acesso: o usuário do app ou o administrador do painel web (ETI-117). Fechado de
 * propósito: o {@link JwtAuthFilter} decide a role com {@code switch} sem {@code default}.
 */
public sealed interface DonoTokenAcesso extends Principal permits UsuarioAutenticado, AdministradorAutenticado {
}
