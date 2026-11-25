package com.limio.api.modulos.auth.actions.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaTotaisDeUsuarios;
import com.limio.api.modulos.auth.usuario.UsuarioService;

/** Implementa {@link ConsultaTotaisDeUsuarios} para o dashboard do administrador (ETI-117). */
@Component
public class ConsultaTotaisDeUsuariosService implements ConsultaTotaisDeUsuarios {

    private final UsuarioService usuarioService;

    public ConsultaTotaisDeUsuariosService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    @Transactional(readOnly = true)
    public long contarContasNaoEncerradas() {
        return usuarioService.contarNaoEncerradas();
    }
}
