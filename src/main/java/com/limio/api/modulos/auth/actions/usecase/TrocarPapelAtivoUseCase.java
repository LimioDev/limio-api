package com.limio.api.modulos.auth.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.excecao.ContaSuspensaException;
import com.limio.api.modulos.auth.excecao.PapelInvalidoException;
import com.limio.api.modulos.auth.excecao.SessaoInvalidaException;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;

/**
 * Regra de negócio do UC09 (alternar entre Empregador e Prestador). O papel
 * fica no {@link Usuario}, então a troca vale pra conta inteira e o próximo
 * login já começa nele. O token de acesso em uso continua com o papel antigo
 * até ser renovado.
 *
 * Fora deste usecase: checar se o perfil de Prestador está completo (fluxo
 * alternativo da UC09) — o módulo {@code perfil} ainda é só esqueleto.
 */
@Service
public class TrocarPapelAtivoUseCase {

    private final UsuarioService usuarioService;

    public TrocarPapelAtivoUseCase(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Transactional
    public Usuario executar(UUID usuarioId, PapelUsuario novoPapel) {
        if (!isAlternavel(novoPapel)) {
            throw new PapelInvalidoException();
        }
        Usuario usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);
        if (usuario.isEncerrada()) {
            throw new SessaoInvalidaException();
        }
        if (usuario.isSuspensa()) {
            throw new ContaSuspensaException();
        }

        usuario.setPapelAtivo(novoPapel);
        return usuarioService.salvar(usuario);
    }

    private static boolean isAlternavel(PapelUsuario papel) {
        return switch (papel) {
            case EMPREGADOR, PRESTADOR -> true;
            case ADMIN -> false;
        };
    }
}
