package com.limio.api.modulos.perfil.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.eventos.ContaEncerradaEvent;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.eventos.FotoPerfilRemovidaEvent;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;

/**
 * Reação de {@code perfil} ao encerramento de conta (UC10, LGPD — pendência do TICKET-0034):
 * <ul>
 *   <li>{@code PerfilEmpregador}: nome de exibição anonimizado (a linha fica, pro histórico de avaliações);</li>
 *   <li>{@code PerfilPrestador}: apagado — localização, raio, especialidades e janelas não servem a mais nada
 *       e as colunas de localização são obrigatórias, então não dá pra só limpá-las;</li>
 *   <li>foto de perfil: expurgada e apagada do storage depois do commit.</li>
 * </ul>
 * Roda depois do commit de {@code auth}, em transação própria. Cada parte é isolada: uma falha é logada e
 * não impede as outras nem volta pra quem encerrou a conta.
 */
@Service
public class AnonimizarPerfilDaContaEncerradaUseCase {

    private static final Logger log = LoggerFactory.getLogger(AnonimizarPerfilDaContaEncerradaUseCase.class);

    private final PerfilEmpregadorService perfilEmpregadorService;
    private final PerfilPrestadorService perfilPrestadorService;
    private final MidiaService midiaService;
    private final ApplicationEventPublisher eventos;

    public AnonimizarPerfilDaContaEncerradaUseCase(PerfilEmpregadorService perfilEmpregadorService,
            PerfilPrestadorService perfilPrestadorService,
            MidiaService midiaService,
            ApplicationEventPublisher eventos) {
        this.perfilEmpregadorService = perfilEmpregadorService;
        this.perfilPrestadorService = perfilPrestadorService;
        this.midiaService = midiaService;
        this.eventos = eventos;
    }

    // REQUIRES_NEW: no AFTER_COMMIT a transação de auth já terminou (mesmo motivo do listener de anuncio).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoEncerrarConta(ContaEncerradaEvent evento) {
        UUID usuarioId = evento.usuarioId();
        isolado("perfil de empregador", usuarioId, () -> perfilEmpregadorService.buscarPorUsuario(usuarioId)
                .ifPresent(perfil -> {
                    perfil.anonimizar();
                    perfilEmpregadorService.salvar(perfil);
                }));
        isolado("perfil de prestador", usuarioId, () -> perfilPrestadorService.apagarDoUsuario(usuarioId));
        isolado("foto de perfil", usuarioId, () -> removerFoto(usuarioId, evento.ocorridoEm()));
    }

    private void removerFoto(UUID usuarioId, Instant agora) {
        midiaService.travarFotoPerfil(usuarioId);
        midiaService.buscarFotoPerfilAtiva(usuarioId).ifPresent(foto -> {
            foto.expurgar(agora);
            midiaService.salvar(foto);
            eventos.publishEvent(new FotoPerfilRemovidaEvent(foto.getUrlS3()));
        });
    }

    private static void isolado(String parte, UUID usuarioId, Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException e) {
            log.error("falha ao limpar {} da conta encerrada {}; demais partes continuam", parte, usuarioId, e);
        }
    }
}
