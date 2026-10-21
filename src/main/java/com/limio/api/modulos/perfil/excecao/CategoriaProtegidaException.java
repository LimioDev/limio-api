package com.limio.api.modulos.perfil.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Tentativa de desativar categoria protegida ("Outros"). Ela precisa estar sempre na lista de
 * especialidades (UC); o banco também barra ({@code ck_categoria_protegida_ativa}), mas lá estouraria
 * como 500 genérico.
 */
public class CategoriaProtegidaException extends NegocioException {

    public CategoriaProtegidaException() {
        super(CodigoErro.CATEGORIA_PROTEGIDA, HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoErro.CATEGORIA_PROTEGIDA.mensagemPadrao());
    }
}
