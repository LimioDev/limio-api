package com.limio.api.comum.seguranca.enums;

/**
 * Papel que o usuário assume na plataforma — alternável após o login. Fica em
 * {@code comum/} (não em {@code modulos/auth}) porque é parte de
 * {@link com.limio.api.comum.seguranca.UsuarioAutenticado}: todo módulo que
 * autoriza por papel precisa do tipo sem importar o módulo {@code auth}.
 *
 * Não existe papel de administrador aqui: administrador é outro ator, com login próprio
 * ({@code administrador} em {@code modulos.administracao}), nunca um {@code Usuario}. Decisão do tech lead
 * (2026-10-26) que fecha a pendência do TICKET-0044.
 */
public enum PapelUsuario {
    EMPREGADOR,
    PRESTADOR
}
