package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.auth.actions.helper.RefreshTokenHelper;
import com.limio.api.modulos.auth.sessao.SessaoService;

/**
 * Regra de negócio do UC08 (logout do dispositivo): revoga só a sessão dona
 * deste refresh token — as dos outros aparelhos continuam ativas.
 *
 * Idempotente: token desconhecido, de outro usuário ou de sessão já revogada
 * não é erro. O app limpa a sessão local do mesmo jeito, e a resposta não
 * revela nada sobre o token recebido.
 */
@Service
public class EncerrarSessaoUseCase {

    private final SessaoService sessaoService;

    public EncerrarSessaoUseCase(SessaoService sessaoService) {
        this.sessaoService = sessaoService;
    }

    @Transactional
    public void executar(UUID usuarioId, String refreshToken) {
        sessaoService.buscarDoUsuarioPorRefreshTokenHash(usuarioId, RefreshTokenHelper.hash(refreshToken))
                .filter(sessao -> sessao.getRevogadaEm() == null)
                .ifPresent(sessao -> {
                    sessao.setRevogadaEm(Instant.now());
                    sessaoService.salvar(sessao);
                });
    }
}
