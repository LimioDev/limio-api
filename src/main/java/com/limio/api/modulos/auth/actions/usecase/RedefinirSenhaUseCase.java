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
 *
 * Consumo atômico: trava a conta e depois o token (ver
 * {@link VerificacaoContatoService#buscarParaConsumir}); dois envios
 * simultâneos do mesmo link não redefinem a senha duas vezes.
 */
@Service
public class RedefinirSenhaUseCase {

    private static final FinalidadeVerificacao FINALIDADE = FinalidadeVerificacao.RECUPERACAO_SENHA;

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
        String hash = TokenRecuperacaoHelper.hash(token);
        Usuario usuario = verificacaoContatoService.buscarUsuarioIdPorTokenHash(hash, FINALIDADE)
                .map(usuarioService::buscarParaAtualizarOuFalhar)
                .orElseThrow(TokenRecuperacaoInvalidoException::new);
        VerificacaoContato verificacao = verificacaoContatoService.buscarParaConsumir(hash, FINALIDADE)
                .filter(encontrada -> encontrada.isValido(agora))
                .orElseThrow(TokenRecuperacaoInvalidoException::new);
        // D10: e-mail trocado depois do pedido → o link enviado ao endereço antigo não vale mais
        if (usuario.isEncerrada()
                || !verificacao.valeParaDestino(TokenRecuperacaoHelper.hashDestino(usuario.getEmail()))) {
            throw new TokenRecuperacaoInvalidoException();
        }

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
