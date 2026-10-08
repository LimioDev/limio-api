package com.limio.api.comum.seguranca.enums;

/**
 * Papel que o usuário assume na plataforma — alternável após o login. Fica em
 * {@code comum/} (não em {@code modulos/auth}) porque é parte de
 * {@link com.limio.api.comum.seguranca.UsuarioAutenticado}: todo módulo que
 * autoriza por papel precisa do tipo sem importar o módulo {@code auth}.
 */
public enum PapelUsuario {
    EMPREGADOR,
    PRESTADOR,
    ADMIN
}
