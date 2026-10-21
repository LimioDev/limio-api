package com.limio.api.modulos.auth.actions.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.auth.actions.helper.TelefoneHelper;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;

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
                TelefoneHelper.paraInternacional(usuario.getTelefone()).orElse(null),
                usuario.getEmailConfirmadoEm(),
                usuario.getTelefoneConfirmadoEm());
    }
}
