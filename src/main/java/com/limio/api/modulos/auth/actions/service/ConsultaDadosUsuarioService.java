package com.limio.api.modulos.auth.actions.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;

/**
 * Adapter que expõe o {@link Usuario} a outros módulos pela porta {@link ConsultaDadosUsuario},
 * sem vazar a entity. Conta encerrada responde como inexistente.
 */
@Component
public class ConsultaDadosUsuarioService implements ConsultaDadosUsuario {

    private final UsuarioService usuarioService;

    public ConsultaDadosUsuarioService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public Optional<DadosUsuario> buscarPorId(UUID usuarioId) {
        return usuarioService.buscarPorId(usuarioId)
                .filter(usuario -> !usuario.isEncerrada())
                .map(ConsultaDadosUsuarioService::toDados);
    }

    private static DadosUsuario toDados(Usuario usuario) {
        return new DadosUsuario(
                usuario.getNomeCompleto(),
                usuario.getEmail(),
                usuario.getTelefone(),
                usuario.getEmailConfirmadoEm(),
                usuario.getTelefoneConfirmadoEm());
    }
}
