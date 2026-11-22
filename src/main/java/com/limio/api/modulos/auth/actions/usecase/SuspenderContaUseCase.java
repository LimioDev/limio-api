package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.ContaSuspensaEvent;
import com.limio.api.modulos.auth.sessao.SessaoService;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;

/**
 * Suspensão de conta pela moderação (ETI-116), do lado do {@code auth}.
 *
 * <ul>
 *   <li>{@link #aoSuspenderConta}: na transação da decisão do administrador, a conta fica {@code BLOQUEADA} até o fim
 *       da suspensão e todas as sessões são encerradas. Daí em diante o login e a renovação respondem
 *       {@code CONTA_SUSPENSA}.</li>
 *   <li>{@link #encerrarSuspensoesVencidas}: rotina ({@code auth.suspensao.cron}) que devolve a {@code ATIVA} as contas
 *       cujo prazo passou. A pessoa entra de novo pelo login.</li>
 * </ul>
 */
@Service
public class SuspenderContaUseCase {

    private static final Logger log = LoggerFactory.getLogger(SuspenderContaUseCase.class);

    private final UsuarioService usuarioService;
    private final SessaoService sessaoService;

    public SuspenderContaUseCase(UsuarioService usuarioService, SessaoService sessaoService) {
        this.usuarioService = usuarioService;
        this.sessaoService = sessaoService;
    }

    /** Conta já encerrada não tem o que suspender: a decisão segue, e não há sessão aberta. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void aoSuspenderConta(ContaSuspensaEvent evento) {
        Usuario usuario = usuarioService.buscarParaAtualizarOuFalhar(evento.usuarioId());
        if (usuario.isEncerrada()) {
            return;
        }
        usuario.suspender(evento.suspensaAte());
        usuarioService.salvar(usuario);
        sessaoService.revogarTodas(usuario.getId(), null, evento.ocorridoEm());
    }

    @Scheduled(cron = "${auth.suspensao.cron}")
    @Transactional
    public void encerrarSuspensoesVencidas() {
        List<Usuario> vencidas = usuarioService.buscarComSuspensaoVencida(Instant.now());
        vencidas.forEach(usuario -> {
            usuario.encerrarSuspensao();
            usuarioService.salvar(usuario);
        });
        if (!vencidas.isEmpty()) {
            log.info("{} suspensão(ões) vencida(s) encerrada(s)", vencidas.size());
        }
    }
}
