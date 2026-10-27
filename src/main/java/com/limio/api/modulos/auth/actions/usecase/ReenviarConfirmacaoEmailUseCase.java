package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.verificacaocontato.EmissorConfirmacaoEmail;

/**
 * "Reenviar link" da tela de confirmação de e-mail do app (TICKET-0047). Rota pública: depois do
 * cadastro o usuário ainda não entrou. Anti-enumeração, como a recuperação de senha: quem chama sempre
 * recebe a mesma resposta, então este método não devolve nada nem lança exceção de negócio.
 *
 * Só reenvia se a conta existe, não está encerrada, o e-mail ainda não foi confirmado e o limite de
 * emissões não bateu. Trava a conta antes de decidir (regra do TICKET-0048 pra todo emissor de
 * {@code CONFIRMACAO_EMAIL}).
 */
@Service
public class ReenviarConfirmacaoEmailUseCase {

    private final UsuarioService usuarioService;
    private final EmissorConfirmacaoEmail emissorConfirmacaoEmail;

    public ReenviarConfirmacaoEmailUseCase(UsuarioService usuarioService,
            EmissorConfirmacaoEmail emissorConfirmacaoEmail) {
        this.usuarioService = usuarioService;
        this.emissorConfirmacaoEmail = emissorConfirmacaoEmail;
    }

    @Transactional
    public void executar(String email) {
        Instant agora = Instant.now();
        usuarioService.buscarIdPorEmail(email)
                .map(usuarioService::buscarParaAtualizarOuFalhar)
                .filter(usuario -> podeReceber(usuario, email, agora))
                .ifPresent(usuario -> emissorConfirmacaoEmail.emitir(usuario, agora));
    }

    private boolean podeReceber(Usuario usuario, String email, Instant agora) {
        // e-mail relido depois do lock: uma troca concorrente pode ter mudado o endereço
        return !usuario.isEncerrada()
                && usuario.getEmailConfirmadoEm() == null
                && email.equalsIgnoreCase(usuario.getEmail())
                && !emissorConfirmacaoEmail.limiteAtingido(usuario.getId(), agora);
    }
}
