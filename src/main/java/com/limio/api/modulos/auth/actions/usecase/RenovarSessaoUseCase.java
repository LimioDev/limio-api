package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.auth.actions.helper.RefreshTokenHelper;
import com.limio.api.modulos.auth.actions.service.TokenService;
import com.limio.api.modulos.auth.excecao.SessaoInvalidaException;
import com.limio.api.modulos.auth.sessao.Sessao;
import com.limio.api.modulos.auth.sessao.SessaoService;
import com.limio.api.modulos.auth.sessao.TokensSessao;
import com.limio.api.modulos.auth.usuario.Usuario;

/**
 * Renova o token de acesso a partir do refresh token, rotacionando o refresh
 * token (o usado deixa de valer). O papel do token novo vem do usuário, então
 * já reflete uma troca de papel feita em qualquer aparelho.
 */
@Service
public class RenovarSessaoUseCase {

    private final SessaoService sessaoService;
    private final TokenService tokenService;

    public RenovarSessaoUseCase(SessaoService sessaoService, TokenService tokenService) {
        this.sessaoService = sessaoService;
        this.tokenService = tokenService;
    }

    @Transactional
    public TokensSessao executar(String refreshToken) {
        Instant agora = Instant.now();
        Sessao sessao = sessaoService.buscarPorRefreshTokenHashParaRenovar(RefreshTokenHelper.hash(refreshToken))
                .filter(encontrada -> encontrada.isAtiva(agora))
                .orElseThrow(SessaoInvalidaException::new);

        Usuario usuario = sessao.getUsuario();
        usuario.exigirAtiva();

        TokensSessao tokens = tokenService.emitir(usuario, agora);
        sessao.rotacionar(tokens, agora);
        sessaoService.salvar(sessao);
        return tokens;
    }
}
