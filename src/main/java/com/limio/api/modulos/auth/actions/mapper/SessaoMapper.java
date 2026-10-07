package com.limio.api.modulos.auth.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.sessao.TokensSessao;

/** {@link TokensSessao} -> {@link LoginResponse}. Chamado só pelo controller. */
@Component
public class SessaoMapper {

    public LoginResponse toLoginResponse(TokensSessao tokens) {
        return new LoginResponse(tokens.tokenAcesso(), tokens.tokenAcessoExpiraEm(), tokens.papel(), tokens.refreshToken());
    }
}
