package com.limio.api.modulos.financeiro.actions.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.eventos.CobrancaGoldAprovadaEvent;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.modulos.financeiro.eventos.GoldAssinadoEvent;

/**
 * Envia o recibo da assinatura GOLD (ETI-81) e de cada renovação paga (ETI-85) só depois do commit, mesmo padrão de {@code
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
        enviarRecibo(evento.usuarioId(), evento.nomePlano(), evento.valorCentavos(), evento.moeda(), evento.renovaEm(),
                evento.numeroRecibo());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRenovarGold(CobrancaGoldAprovadaEvent evento) {
        enviarRecibo(evento.usuarioId(), evento.nomePlano(), evento.valorCentavos(), evento.moeda(),
                evento.proximaCobrancaEm(), evento.numeroRecibo());
    }

    private void enviarRecibo(UUID usuarioId, String nomePlano, long valorCentavos, String moeda, Instant renovaEm,
            String numeroRecibo) {
        try {
            consultaDadosUsuario.buscarPorId(usuarioId).ifPresent(dados ->
                    emailService.enviarReciboGold(dados.email(), nomePlano, valorCentavos, moeda,
                            renovaEm.atZone(ZoneOffset.UTC), numeroRecibo));
        } catch (RuntimeException e) {
            log.error("falha ao enviar recibo do GOLD pós-commit", e);
        }
    }
}
