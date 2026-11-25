package com.limio.api.modulos.auth.actions.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
 *       {@code CONTA_SUSPENSA}. Nunca encurta um bloqueio que já vale ({@link Usuario#suspender}).</li>
 *   <li>{@link #encerrarSuspensoesVencidas}: rotina ({@code auth.suspensao.cron}) que devolve a {@code ATIVA} as contas
 *       cujo prazo passou. A pessoa entra de novo pelo login.</li>
 * </ul>
 *
 * <p>A rotina segue o padrão de {@code ExpirarAnunciosSemCandidatosUseCase}: a busca traz só os ids, e cada conta é
 * travada ({@code SELECT ... FOR UPDATE}, a mesma trava da suspensão) e conferida de novo na própria transação. Assim
 * uma suspensão aplicada no meio da rotina não é desfeita, e uma falha numa conta não desfaz as outras.
 */
@Service
public class SuspenderContaUseCase {

    private static final Logger log = LoggerFactory.getLogger(SuspenderContaUseCase.class);

    private final UsuarioService usuarioService;
    private final SessaoService sessaoService;
    private final TransactionTemplate transacao;
    private final Clock relogio;

    public SuspenderContaUseCase(UsuarioService usuarioService, SessaoService sessaoService,
            TransactionTemplate transacao, Clock relogio) {
        this.usuarioService = usuarioService;
        this.sessaoService = sessaoService;
        this.transacao = transacao;
        this.relogio = relogio;
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

    /** Não é transacional: cada conta tem a própria transação. */
    @Scheduled(cron = "${auth.suspensao.cron}")
    public void encerrarSuspensoesVencidas() {
        Instant agora = Instant.now(relogio);
        List<UUID> candidatas = usuarioService.buscarIdsComSuspensaoVencida(agora);
        int encerradas = 0;
        for (UUID usuarioId : candidatas) {
            try {
                if (Boolean.TRUE.equals(transacao.execute(status -> encerrarSeVenceu(usuarioId, agora)))) {
                    encerradas++;
                }
            } catch (RuntimeException e) {
                log.error("falha ao encerrar a suspensão vencida do usuário {}", usuarioId, e);
            }
        }
        if (encerradas > 0) {
            log.info("{} suspensão(ões) vencida(s) encerrada(s)", encerradas);
        }
    }

    /** Com a linha travada: só reativa se a suspensão ainda é a vencida (nada a estendeu nem encerrou a conta). */
    private boolean encerrarSeVenceu(UUID usuarioId, Instant agora) {
        Usuario usuario = usuarioService.buscarParaAtualizarOuFalhar(usuarioId);
        if (!usuario.suspensaoVenceuEm(agora)) {
            return false;
        }
        usuario.encerrarSuspensao();
        usuarioService.salvar(usuario);
        return true;
    }
}
