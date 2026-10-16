package com.limio.api.modulos.auth.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.excecao.SenhaAtualIncorretaException;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;

@Service
public class TrocarTelefoneUseCase {

    private final UsuarioService usuarioService;
    private final SenhaHasher senhaHasher;

    public TrocarTelefoneUseCase(UsuarioService usuarioService, SenhaHasher senhaHasher) {
        this.usuarioService = usuarioService;
        this.senhaHasher = senhaHasher;
    }

    @Transactional
    public Usuario executar(UUID usuarioId, String novoTelefone, String senhaAtual) {
        Usuario usuario = usuarioService.buscarParaAtualizarOuFalhar(usuarioId);
        usuario.exigirAtiva();
        if (!senhaHasher.confere(senhaAtual, usuario.getSenhaHash())) {
            throw new SenhaAtualIncorretaException();
        }
        if (novoTelefone.equals(usuario.getTelefone())) {
            return usuario;
        }

        usuario.setTelefone(novoTelefone);
        usuario.setTelefoneConfirmadoEm(null);
        return usuarioService.salvar(usuario);
    }
}
