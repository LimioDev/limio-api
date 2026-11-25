package com.limio.api.comum.seguranca;

import java.util.UUID;

/**
 * Administrador logado no painel web, lido do token de acesso pelo {@link JwtAuthFilter}. É outro ator, não um
 * {@code Usuario}: o token dele traz a claim {@link JwtAuthFilter#CLAIM_TIPO} =
 * {@link JwtAuthFilter#TIPO_ADMINISTRADOR} e nenhum papel. Só as rotas {@code /admin/**} o aceitam, e as rotas do
 * app só aceitam {@link UsuarioAutenticado} ({@code SecurityConfig}).
 *
 * <p>Controller recebe via {@code @AuthenticationPrincipal}, como o {@link UsuarioAutenticado}. O {@code id} é o da
 * tabela {@code administrador} ({@code modulos.administracao}).
 */
public record AdministradorAutenticado(UUID id) implements DonoTokenAcesso {

    @Override
    public String getName() {
        return id.toString();
    }
}
