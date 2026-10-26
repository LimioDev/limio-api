package com.limio.api.modulos.auth.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;

/**
 * Regra de negócio do UC09 (alternar entre Empregador e Prestador). O papel
 * fica no {@link Usuario}, então a troca vale pra conta inteira e o próximo
 * login já começa nele. O token de acesso em uso continua com o papel antigo
 * até ser renovado.
 *
 * Fora deste usecase: checar se o perfil de Prestador está completo (fluxo
 * alternativo da UC09). O {@code perfil} já calcula {@code completo}; falta o PO
 * definir o que acontece com perfil incompleto (bloquear a troca ou só avisar).
 */
@Service
public class TrocarPapelAtivoUseCase {

    private final UsuarioService usuarioService;

    public TrocarPapelAtivoUseCase(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Transactional
    public Usuario executar(UUID usuarioId, PapelUsuario novoPapel) {
        Usuario usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);
        usuario.exigirAtiva();

        usuario.setPapelAtivo(novoPapel);
        return usuarioService.salvar(usuario);
    }
}
