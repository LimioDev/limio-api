package com.limio.api.modulos.auth.actions.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaContasParaModeracao;
import com.limio.api.comum.portas.ContaParaModeracao;
import com.limio.api.comum.portas.enums.SituacaoConta;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;

/** Implementa {@link ConsultaContasParaModeracao} para a fila e a análise de denúncias do administrador (ETI-116). */
@Component
public class ConsultaContasParaModeracaoService implements ConsultaContasParaModeracao {

    private final UsuarioService usuarioService;

    public ConsultaContasParaModeracaoService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ContaParaModeracao> buscarPorIds(Collection<UUID> usuarioIds) {
        if (usuarioIds.isEmpty()) {
            return Map.of();
        }
        return usuarioService.buscarPorIds(usuarioIds).stream()
                .collect(Collectors.toMap(Usuario::getId, ConsultaContasParaModeracaoService::toConta));
    }

    private static ContaParaModeracao toConta(Usuario usuario) {
        return new ContaParaModeracao(usuario.getId(), usuario.getNomeCompleto(), usuario.getPapelAtivo(),
                situacao(usuario));
    }

    private static SituacaoConta situacao(Usuario usuario) {
        if (usuario.isEncerrada()) {
            return SituacaoConta.ENCERRADA;
        }
        return switch (usuario.getStatusConta()) {
            case ATIVA -> SituacaoConta.ATIVA;
            case PENDENTE_VERIFICACAO -> SituacaoConta.PENDENTE_VERIFICACAO;
            case BLOQUEADA -> SituacaoConta.SUSPENSA;
        };
    }
}
