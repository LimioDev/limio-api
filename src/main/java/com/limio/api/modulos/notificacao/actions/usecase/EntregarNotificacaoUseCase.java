package com.limio.api.modulos.notificacao.actions.usecase;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.config.AssincronoConfig;
import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.NotificacaoService;
import com.limio.api.modulos.notificacao.actions.service.CanalDeEnvioService;
import com.limio.api.modulos.notificacao.enums.CanalNotificacao;
import com.limio.api.modulos.notificacao.envionotificacao.EnvioNotificacao;
import com.limio.api.modulos.notificacao.envionotificacao.EnvioNotificacaoService;
import com.limio.api.modulos.notificacao.eventos.NotificacaoRegistradaEvent;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacaoService;

/**
 * UC "Enviar notificações respeitando as preferências" (ETI-112), segunda metade: envia a notificação já gravada só
 * pelos canais que o usuário ligou e registra cada tentativa em {@link EnvioNotificacao}. Como cada canal envia fica
 * na sua {@link CanalDeEnvioService} (push, e-mail, WhatsApp); o WhatsApp nunca envia enquanto não existir o
 * consentimento ({@code CanalWhatsappService}). SMS saiu de escopo no ETI-134.
 *
 * <p>Roda depois do commit que gravou a notificação, fora da requisição ({@code @Async}): a ação que gerou o aviso não
 * espera nenhum provedor. Falha num canal vira linha {@code FALHOU} e não impede os outros; a notificação continua na
 * lista do app. Os e-mails de segurança (senha alterada, e-mail alterado) não passam por aqui: o {@code auth} envia
 * sempre, mesmo com o e-mail desligado (decisão do PO).
 */
@Service
public class EntregarNotificacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(EntregarNotificacaoUseCase.class);

    private final NotificacaoService notificacaoService;
    private final PreferenciaNotificacaoService preferenciaNotificacaoService;
    private final EnvioNotificacaoService envioNotificacaoService;
    private final Map<CanalNotificacao, CanalDeEnvioService> canais;
    private final Clock relogio;

    public EntregarNotificacaoUseCase(NotificacaoService notificacaoService,
            PreferenciaNotificacaoService preferenciaNotificacaoService,
            EnvioNotificacaoService envioNotificacaoService,
            List<CanalDeEnvioService> canais,
            Clock relogio) {
        this.notificacaoService = notificacaoService;
        this.preferenciaNotificacaoService = preferenciaNotificacaoService;
        this.envioNotificacaoService = envioNotificacaoService;
        this.canais = porCanal(canais);
        this.relogio = relogio;
    }

    /** Um envio por canal, na ordem do enum. Falta de implementação pra um canal derruba a subida, não o envio. */
    private static Map<CanalNotificacao, CanalDeEnvioService> porCanal(List<CanalDeEnvioService> canais) {
        Map<CanalNotificacao, CanalDeEnvioService> porCanal = new EnumMap<>(CanalNotificacao.class);
        for (CanalDeEnvioService canal : canais) {
            if (porCanal.put(canal.canal(), canal) != null) {
                throw new IllegalStateException("mais de um envio pro canal " + canal.canal());
            }
        }
        for (CanalNotificacao canal : CanalNotificacao.values()) {
            if (!porCanal.containsKey(canal)) {
                throw new IllegalStateException("nenhum envio pro canal " + canal);
            }
        }
        return porCanal;
    }

    @Async(AssincronoConfig.ENVIO_DE_AVISOS)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRegistrarNotificacao(NotificacaoRegistradaEvent evento) {
        notificacaoService.buscarPorId(evento.notificacaoId()).ifPresentOrElse(this::entregar,
                () -> log.warn("notificação {} sumiu antes do envio", evento.notificacaoId()));
    }

    private void entregar(Notificacao notificacao) {
        UUID usuarioId = notificacao.getUsuarioId();
        PreferenciaNotificacao preferencia = preferenciaNotificacaoService.buscarPorUsuario(usuarioId)
                .orElseGet(() -> PreferenciaNotificacao.padraoContaNova(usuarioId));

        List<EnvioNotificacao> envios = new ArrayList<>();
        canais.forEach((canal, envio) -> {
            if (preferencia.ligado(canal)) {
                enviar(notificacao, envio).ifPresent(envios::add);
            }
        });
        envioNotificacaoService.salvarTodos(envios);
    }

    /** Vazio quando não houve tentativa. */
    private Optional<EnvioNotificacao> enviar(Notificacao notificacao, CanalDeEnvioService envio) {
        try {
            return envio.enviar(notificacao)
                    ? Optional.of(EnvioNotificacao.enviado(notificacao.getId(), envio.canal(), relogio.instant()))
                    : Optional.empty();
        } catch (RuntimeException e) {
            log.error("falha ao enviar a notificação {} por {}", notificacao.getId(), envio.canal(), e);
            return Optional.of(EnvioNotificacao.falhou(notificacao.getId(), envio.canal(), relogio.instant(),
                    e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }
}
