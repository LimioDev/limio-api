package com.limio.api.modulos.financeiro.actions.service;

import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.modulos.financeiro.eventos.GoldAssinadoEvent;

/**
 * Envia o recibo da assinatura GOLD só depois do commit (ETI-81), mesmo padrão de {@code
 * auth.EmailEventosService}: sem {@code fallbackExecution}, e falha de envio é logada e não propaga —
 * o pagamento já foi aprovado e persistido, um e-mail que falha não pode desfazer isso.
 */
@Component
public class ReciboEventosService {

    private static final Logger log = LoggerFactory.getLogger(ReciboEventosService.class);

    private final ReciboGoldEmailService emailService;
    private final ConsultaDadosUsuario consultaDadosUsuario;

    public ReciboEventosService(ReciboGoldEmailService emailService, ConsultaDadosUsuario consultaDadosUsuario) {
        this.emailService = emailService;
        this.consultaDadosUsuario = consultaDadosUsuario;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAssinarGold(GoldAssinadoEvent evento) {
        try {
            consultaDadosUsuario.buscarPorId(evento.usuarioId()).ifPresent(dados ->
                    emailService.enviarReciboGold(dados.email(), evento.nomePlano(), evento.valorCentavos(),
                            evento.moeda(), evento.renovaEm().atZone(ZoneOffset.UTC), evento.numeroRecibo()));
        } catch (RuntimeException e) {
            log.error("falha ao enviar recibo da assinatura GOLD pós-commit", e);
        }
    }
}
