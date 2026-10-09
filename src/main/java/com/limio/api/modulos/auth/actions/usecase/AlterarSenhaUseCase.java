package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.auth.actions.helper.RefreshTokenHelper;
import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.actions.service.EmailService;
import com.limio.api.modulos.auth.actions.service.TokenService;
import com.limio.api.modulos.auth.excecao.SenhaAtualIncorretaException;
import com.limio.api.modulos.auth.excecao.SenhaIgualAnteriorException;
import com.limio.api.modulos.auth.excecao.SessaoInvalidaException;
import com.limio.api.modulos.auth.sessao.Sessao;
import com.limio.api.modulos.auth.sessao.SessaoService;
import com.limio.api.modulos.auth.sessao.TokensSessao;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;

/**
 * Regra de negócio do UC07 (alterar senha, usuário logado): confere a senha
 * atual, grava a nova, mantém a sessão deste aparelho (tokens novos) e
 * encerra as dos outros. {@code refreshToken} identifica qual {@code Sessao}
 * é "este aparelho" — o token de acesso (JWT) não carrega id de sessão.
 */
@Service
public class AlterarSenhaUseCase {

    private final UsuarioService usuarioService;
    private final SessaoService sessaoService;
    private final SenhaHasher senhaHasher;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final String frontendUrl;

    public AlterarSenhaUseCase(UsuarioService usuarioService,
            SessaoService sessaoService,
            SenhaHasher senhaHasher,
            TokenService tokenService,
            EmailService emailService,
            @Value("${app.frontend.url}") String frontendUrl) {
        this.usuarioService = usuarioService;
        this.sessaoService = sessaoService;
        this.senhaHasher = senhaHasher;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public TokensSessao executar(UUID usuarioId, String senhaAtual, String novaSenha, String refreshTokenAtual) {
        Instant agora = Instant.now();
        Usuario usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);
        usuario.exigirAtiva();

        if (!senhaHasher.confere(senhaAtual, usuario.getSenhaHash())) {
            throw new SenhaAtualIncorretaException();
        }
        if (senhaHasher.confere(novaSenha, usuario.getSenhaHash())) {
            throw new SenhaIgualAnteriorException();
        }

        Sessao sessaoAtual = sessaoService
                .buscarDoUsuarioPorRefreshTokenHash(usuarioId, RefreshTokenHelper.hash(refreshTokenAtual))
                .orElseThrow(SessaoInvalidaException::new);

        usuario.setSenhaHash(senhaHasher.hash(novaSenha));
        usuarioService.salvar(usuario);

        TokensSessao tokens = tokenService.emitir(usuario, agora);
        sessaoAtual.rotacionar(tokens, agora);
        sessaoService.salvar(sessaoAtual);
        sessaoService.revogarTodas(usuarioId, sessaoAtual.getId(), agora);

        emailService.enviarSenhaAlterada(usuario.getEmail(), frontendUrl + "/recuperar-senha");
        return tokens;
    }
}
