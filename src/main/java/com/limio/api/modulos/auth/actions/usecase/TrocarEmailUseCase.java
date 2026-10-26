package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.eventos.EmailContaAlteradoEvent;
import com.limio.api.modulos.auth.excecao.ContatoNaoPermitidoException;
import com.limio.api.modulos.auth.excecao.SenhaAtualIncorretaException;
import com.limio.api.modulos.auth.excecao.TrocaContatoLimiteExcedidaException;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;

@Service
public class TrocarEmailUseCase {

    private final UsuarioService usuarioService;
    private final SenhaHasher senhaHasher;
    private final ApplicationEventPublisher eventos;
    private final EmitirConfirmacaoEmailUseCase emitirConfirmacaoEmail;

    public TrocarEmailUseCase(UsuarioService usuarioService,
            SenhaHasher senhaHasher,
            ApplicationEventPublisher eventos,
            EmitirConfirmacaoEmailUseCase emitirConfirmacaoEmail) {
        this.usuarioService = usuarioService;
        this.senhaHasher = senhaHasher;
        this.eventos = eventos;
        this.emitirConfirmacaoEmail = emitirConfirmacaoEmail;
    }

    @Transactional
    public Usuario executar(UUID usuarioId, String novoEmail, String senhaAtual) {
        Usuario usuario = usuarioService.buscarParaAtualizarOuFalhar(usuarioId);
        Instant agora = Instant.now();
        usuario.exigirAtiva();
        if (!senhaHasher.confere(senhaAtual, usuario.getSenhaHash())) {
            throw new SenhaAtualIncorretaException();
        }

        boolean mesmoEmail = novoEmail.equalsIgnoreCase(usuario.getEmail());
        if (mesmoEmail && usuario.getEmailConfirmadoEm() != null) {
            return usuario;
        }
        if (!mesmoEmail) {
            validarDisponivel(usuarioId, novoEmail);
        }
        if (emitirConfirmacaoEmail.limiteAtingido(usuarioId, agora)) {
            throw new TrocaContatoLimiteExcedidaException();
        }

        String emailAntigo = usuario.getEmail();
        if (!mesmoEmail) {
            usuario.setEmail(novoEmail);
            usuario.setEmailConfirmadoEm(null);
            usuarioService.salvarComEmailUnico(usuario);
        }

        emitirConfirmacaoEmail.emitir(usuario, agora);
        if (!mesmoEmail) {
            eventos.publishEvent(new EmailContaAlteradoEvent(emailAntigo, novoEmail));
        }
        return usuario;
    }

    private void validarDisponivel(UUID usuarioId, String novoEmail) {
        boolean deOutraConta = usuarioService.buscarPorEmail(novoEmail)
                .filter(outro -> !outro.getId().equals(usuarioId))
                .isPresent();
        if (deOutraConta) {
            throw new ContatoNaoPermitidoException();
        }
    }
}
