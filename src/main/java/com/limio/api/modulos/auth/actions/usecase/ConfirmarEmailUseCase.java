package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.auth.actions.helper.TokenRecuperacaoHelper;
import com.limio.api.modulos.auth.excecao.LinkConfirmacaoEmailInvalidoException;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContato;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoService;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

/**
 * UC05 — confirma o e-mail pelo link (TICKET-0047), seguindo o contrato do TICKET-0048:
 * <ol>
 *   <li>trava a conta ({@code buscarParaAtualizarOuFalhar}) e só então relê o token, também travado —
 *       fecha a corrida com uma troca de e-mail simultânea, que invalida os links pendentes;</li>
 *   <li>recusa conta encerrada;</li>
 *   <li>consome o token sob esses locks: dois cliques no mesmo link não confirmam duas vezes;</li>
 *   <li>recusa token emitido para outro e-mail ({@code destino_hash}, D10).</li>
 * </ol>
 * Token inexistente, expirado, já usado ou de conta encerrada têm a mesma resposta.
 */
@Service
public class ConfirmarEmailUseCase {

    private static final FinalidadeVerificacao FINALIDADE = FinalidadeVerificacao.CONFIRMACAO_EMAIL;

    private final UsuarioService usuarioService;
    private final VerificacaoContatoService verificacaoContatoService;

    public ConfirmarEmailUseCase(UsuarioService usuarioService, VerificacaoContatoService verificacaoContatoService) {
        this.usuarioService = usuarioService;
        this.verificacaoContatoService = verificacaoContatoService;
    }

    @Transactional
    public void executar(String token) {
        Instant agora = Instant.now();
        String hash = TokenRecuperacaoHelper.hash(token);
        Usuario usuario = verificacaoContatoService.buscarUsuarioIdPorTokenHash(hash, FINALIDADE)
                .map(usuarioService::buscarParaAtualizarOuFalhar)
                .orElseThrow(LinkConfirmacaoEmailInvalidoException::new);
        VerificacaoContato verificacao = verificacaoContatoService.buscarParaConsumir(hash, FINALIDADE)
                .filter(encontrada -> encontrada.isValido(agora))
                .orElseThrow(LinkConfirmacaoEmailInvalidoException::new);
        if (usuario.isEncerrada()
                || !verificacao.valeParaDestino(TokenRecuperacaoHelper.hashDestino(usuario.getEmail()))) {
            throw new LinkConfirmacaoEmailInvalidoException();
        }

        usuario.setEmailConfirmadoEm(agora);
        usuarioService.salvar(usuario);

        verificacao.consumir(agora);
        verificacaoContatoService.salvar(verificacao);
    }
}
