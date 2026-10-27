package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.auth.excecao.LinkConfirmacaoEmailInvalidoException;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.verificacaocontato.ConsumoTokenVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.UsuarioEVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContato;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoService;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

/**
 * UC05 — confirma o e-mail pelo link (TICKET-0047), seguindo o contrato do TICKET-0048: trava a conta e
 * o token ({@link ConsumoTokenVerificacao#consumirValido}, ordem conta → token, fecha a corrida com uma
 * troca de e-mail simultânea), recusa conta encerrada e token emitido para outro e-mail
 * ({@code destino_hash}, D10), e só então consome o token — dois cliques no mesmo link não confirmam
 * duas vezes. Token inexistente, expirado, já usado ou de conta encerrada têm a mesma resposta.
 */
@Service
public class ConfirmarEmailUseCase {

    private static final FinalidadeVerificacao FINALIDADE = FinalidadeVerificacao.CONFIRMACAO_EMAIL;

    private final UsuarioService usuarioService;
    private final VerificacaoContatoService verificacaoContatoService;
    private final ConsumoTokenVerificacao consumoTokenVerificacao;

    public ConfirmarEmailUseCase(UsuarioService usuarioService, VerificacaoContatoService verificacaoContatoService,
            ConsumoTokenVerificacao consumoTokenVerificacao) {
        this.usuarioService = usuarioService;
        this.verificacaoContatoService = verificacaoContatoService;
        this.consumoTokenVerificacao = consumoTokenVerificacao;
    }

    @Transactional
    public void executar(String token) {
        Instant agora = Instant.now();
        UsuarioEVerificacao travados = consumoTokenVerificacao.consumirValido(token, FINALIDADE, agora,
                LinkConfirmacaoEmailInvalidoException::new);
        Usuario usuario = travados.usuario();
        VerificacaoContato verificacao = travados.verificacao();

        usuario.setEmailConfirmadoEm(agora);
        usuarioService.salvar(usuario);

        verificacao.consumir(agora);
        verificacaoContatoService.salvar(verificacao);
    }
}
