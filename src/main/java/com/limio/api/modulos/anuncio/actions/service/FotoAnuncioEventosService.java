package com.limio.api.modulos.anuncio.actions.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.modulos.anuncio.eventos.FotoAnuncioEnviadaEvent;

/**
 * Limpeza do storage quando o envio de fotos do anúncio é desfeito. Sem {@code fallbackExecution}: fora de
 * transação o evento é descartado (o usecase que publica é {@code @Transactional}).
 */
@Component
public class FotoAnuncioEventosService {

    private final ArmazenamentoService armazenamentoService;

    public FotoAnuncioEventosService(ArmazenamentoService armazenamentoService) {
        this.armazenamentoService = armazenamentoService;
    }

    /** Envio desfeito depois do upload: o objeto não tem Midia apontando pra ele, então sai do bucket. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void aoDesfazerEnvio(FotoAnuncioEnviadaEvent evento) {
        armazenamentoService.remover(evento.chave());
    }
}
