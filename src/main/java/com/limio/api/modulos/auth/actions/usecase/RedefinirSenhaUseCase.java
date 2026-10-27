package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.excecao.SenhaIgualAnteriorException;
import com.limio.api.modulos.auth.excecao.TokenRecuperacaoInvalidoException;
import com.limio.api.modulos.auth.sessao.SessaoService;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.verificacaocontato.ConsumoTokenVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.UsuarioEVerificacao;
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
 * {@link ConsumoTokenVerificacao#consumirValido}); dois envios
 * simultâneos do mesmo link não redefinem a senha duas vezes.
 */
@Service
public class RedefinirSenhaUseCase {

    private static final FinalidadeVerificacao FINALIDADE = FinalidadeVerificacao.RECUPERACAO_SENHA;

    private final UsuarioService usuarioService;
    private final VerificacaoContatoService verificacaoContatoService;
    private final ConsumoTokenVerificacao consumoTokenVerificacao;
    private final SessaoService sessaoService;
    private final SenhaHasher senhaHasher;

    public RedefinirSenhaUseCase(UsuarioService usuarioService,
            VerificacaoContatoService verificacaoContatoService,
            ConsumoTokenVerificacao consumoTokenVerificacao,
            SessaoService sessaoService,
            SenhaHasher senhaHasher) {
        this.usuarioService = usuarioService;
        this.verificacaoContatoService = verificacaoContatoService;
        this.consumoTokenVerificacao = consumoTokenVerificacao;
        this.sessaoService = sessaoService;
        this.senhaHasher = senhaHasher;
    }

    @Transactional
    public void executar(String token, String novaSenha) {
        Instant agora = Instant.now();
        UsuarioEVerificacao travados = consumoTokenVerificacao.consumirValido(token, FINALIDADE, agora,
                TokenRecuperacaoInvalidoException::new);
        Usuario usuario = travados.usuario();
        VerificacaoContato verificacao = travados.verificacao();

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
