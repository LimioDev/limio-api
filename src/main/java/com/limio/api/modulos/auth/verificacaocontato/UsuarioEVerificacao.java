package com.limio.api.modulos.auth.verificacaocontato;

import com.limio.api.modulos.auth.usuario.Usuario;

/** Par devolvido por {@link ConsumoTokenVerificacao#consumirValido}: a conta travada e o token travado. */
public record UsuarioEVerificacao(Usuario usuario, VerificacaoContato verificacao) {
}
