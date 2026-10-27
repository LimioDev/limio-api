package com.limio.api.modulos.perfil.actions.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.modulos.perfil.eventos.FotoPerfilRemovidaEvent;
import com.limio.api.modulos.perfil.eventos.FotoPerfilTrocadaEvent;

/**
 * Limpeza do storage depois da troca de foto. Sem {@code fallbackExecution}: fora de transação o
 * evento é descartado (o usecase que publica é {@code @Transactional}).
 */
@Component
public class FotoPerfilEventosService {

    private final ArmazenamentoService armazenamentoService;

    public FotoPerfilEventosService(ArmazenamentoService armazenamentoService) {
        this.armazenamentoService = armazenamentoService;
    }

    /** Troca gravada: a foto anterior (já com expurgo_em) sai do bucket. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoConfirmarTroca(FotoPerfilTrocadaEvent evento) {
        if (evento.chaveAnterior() != null)
            armazenamentoService.remover(evento.chaveAnterior());
    }

    /** Troca desfeita depois do upload: o objeto novo não tem Midia apontando pra ele, então sai do bucket. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void aoDesfazerTroca(FotoPerfilTrocadaEvent evento) {
        armazenamentoService.remover(evento.chaveNova());
    }

    /** Foto removida (conta encerrada) e gravada: o objeto sai do bucket. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRemoverFoto(FotoPerfilRemovidaEvent evento) {
        armazenamentoService.remover(evento.chave());
    }
}
