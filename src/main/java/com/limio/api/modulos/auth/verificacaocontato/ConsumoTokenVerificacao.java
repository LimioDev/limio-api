package com.limio.api.modulos.auth.verificacaocontato;

import java.time.Instant;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.modulos.auth.actions.helper.TokenVerificacaoHelper;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

/**
 * Trava e valida um token de uso único antes de consumi-lo (recuperação de senha, confirmação de
 * e-mail): ordem fixa dos locks — conta, depois token — é o que evita deadlock com uma troca de e-mail
 * concorrente, que também trava nessa ordem.
 *
 * Não chama {@link VerificacaoContato#consumir}: quem chama pode ter uma validação de negócio própria
 * entre a trava e o consumo (ex.: {@code SenhaIgualAnteriorException}), e um token não deve ser marcado
 * consumido se essa validação falhar.
 */
@Component
public class ConsumoTokenVerificacao {

    private final UsuarioService usuarioService;
    private final VerificacaoContatoService verificacaoContatoService;

    public ConsumoTokenVerificacao(UsuarioService usuarioService, VerificacaoContatoService verificacaoContatoService) {
        this.usuarioService = usuarioService;
        this.verificacaoContatoService = verificacaoContatoService;
    }

    /**
     * @param excecao lançada se o token não existir, estiver expirado/consumido, a conta estiver
     *                 encerrada ou o token valer para outro e-mail (D10, TICKET-0048)
     */
    public UsuarioEVerificacao consumirValido(String token, FinalidadeVerificacao finalidade, Instant agora,
            Supplier<? extends NegocioException> excecao) {
        String hash = TokenVerificacaoHelper.hash(token);
        Usuario usuario = verificacaoContatoService.buscarUsuarioIdPorTokenHash(hash, finalidade)
                .map(usuarioService::buscarParaAtualizarOuFalhar)
                .orElseThrow(excecao);
        VerificacaoContato verificacao = verificacaoContatoService.buscarParaConsumir(hash, finalidade)
                .filter(encontrada -> encontrada.isValido(agora))
                .orElseThrow(excecao);
        if (usuario.isEncerrada()
                || !verificacao.valeParaDestino(TokenVerificacaoHelper.hashDestino(usuario.getEmail()))) {
            throw excecao.get();
        }
        return new UsuarioEVerificacao(usuario, verificacao);
    }
}
