package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.actions.helper.TokenRecuperacaoHelper;
import com.limio.api.modulos.auth.excecao.SenhaIgualAnteriorException;
import com.limio.api.modulos.auth.excecao.TokenRecuperacaoInvalidoException;
import com.limio.api.modulos.auth.sessao.SessaoService;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContato;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoService;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

/**
 * Regra de negócio do UC06 (redefinir senha pelo link, "esqueci minha
 * senha"): valida o token de uso único, grava a nova senha, consome o token
 * e encerra todas as sessões do usuário — ninguém continua logado com a
 * senha antiga em nenhum aparelho.
 */
@Service
public class RedefinirSenhaUseCase {

    private final UsuarioService usuarioService;
    private final VerificacaoContatoService verificacaoContatoService;
    private final SessaoService sessaoService;
    private final SenhaHasher senhaHasher;

    public RedefinirSenhaUseCase(UsuarioService usuarioService,
            VerificacaoContatoService verificacaoContatoService,
            SessaoService sessaoService,
            SenhaHasher senhaHasher) {
        this.usuarioService = usuarioService;
        this.verificacaoContatoService = verificacaoContatoService;
        this.sessaoService = sessaoService;
        this.senhaHasher = senhaHasher;
    }

    @Transactional
    public void executar(String token, String novaSenha) {
        Instant agora = Instant.now();
        VerificacaoContato verificacao = verificacaoContatoService
                .buscarPorTokenHashEFinalidade(TokenRecuperacaoHelper.hash(token), FinalidadeVerificacao.RECUPERACAO_SENHA)
                .filter(encontrada -> encontrada.isValido(agora))
                .orElseThrow(TokenRecuperacaoInvalidoException::new);

        Usuario usuario = verificacao.getUsuario();
        if (senhaHasher.confere(novaSenha, usuario.getSenhaHash())) {
            throw new SenhaIgualAnteriorException();
        }

        usuario.setSenhaHash(senhaHasher.hash(novaSenha));
        usuarioService.salvar(usuario);

        verificacao.consumir(agora);
        verificacaoContatoService.salvar(verificacao);

        sessaoService.revogarTodas(usuario.getId(), null, agora);
    }
}
