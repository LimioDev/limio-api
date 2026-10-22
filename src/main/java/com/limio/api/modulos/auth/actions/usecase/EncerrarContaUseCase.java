package com.limio.api.modulos.auth.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.ContaEncerradaEvent;
import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.modulos.auth.cpfbloqueado.CpfBloqueadoService;
import com.limio.api.modulos.auth.sessao.SessaoService;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;

/**
 * Regra de negócio do UC10 (encerrar conta, LGPD — TICKET-0034/ETI-36).
 * Reação do próprio {@code auth}: bloqueia o CPF por 12 meses, anonimiza o
 * {@link Usuario}, encerra todas as sessões (inclusive a atual) e grava a
 * auditoria — tudo numa transação só. Publica {@link ContaEncerradaEvent}
 * depois, pros outros módulos reagirem (perfil, consentimento) sem
 * {@code auth} importar nenhum deles.
 *
 * Idempotente: conta já encerrada não faz nada de novo (evita duplicar
 * {@code CpfBloqueado} ou reabrir sessões já fechadas numa repetição de
 * requisição) — mesmo espírito do logout ({@code EncerrarSessaoUseCase}).
 */
@Service
public class EncerrarContaUseCase {

    private final UsuarioService usuarioService;
    private final CpfBloqueadoService cpfBloqueadoService;
    private final SessaoService sessaoService;
    private final LogAuditoriaService logAuditoriaService;
    private final ApplicationEventPublisher eventPublisher;

    public EncerrarContaUseCase(UsuarioService usuarioService,
            CpfBloqueadoService cpfBloqueadoService,
            SessaoService sessaoService,
            LogAuditoriaService logAuditoriaService,
            ApplicationEventPublisher eventPublisher) {
        this.usuarioService = usuarioService;
        this.cpfBloqueadoService = cpfBloqueadoService;
        this.sessaoService = sessaoService;
        this.logAuditoriaService = logAuditoriaService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void executar(UUID usuarioId, String ip) {
        Instant agora = Instant.now();
        Usuario usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);
        if (usuario.isEncerrada()) {
            return;
        }

        // precisa do CPF original — encerrar() abaixo apaga o campo
        cpfBloqueadoService.bloquear(usuario.getCpf(), agora);

        usuario.encerrar(agora);
        usuarioService.salvar(usuario);

        sessaoService.revogarTodas(usuarioId, null, agora);

        logAuditoriaService.registrar("Usuario", usuarioId, TipoAcaoAuditoria.CONTA_ENCERRADA,
                "auth-encerrar-conta", ip);

        eventPublisher.publishEvent(new ContaEncerradaEvent(usuarioId, agora));
    }
}
