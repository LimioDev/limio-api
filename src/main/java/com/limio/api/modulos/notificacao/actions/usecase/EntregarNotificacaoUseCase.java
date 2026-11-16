package com.limio.api.modulos.notificacao.actions.usecase;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.NotificacaoService;
import com.limio.api.modulos.notificacao.actions.service.EmailAvisoService;
import com.limio.api.modulos.notificacao.actions.service.PushExpoService;
import com.limio.api.modulos.notificacao.aparelhopush.AparelhoPushService;
import com.limio.api.modulos.notificacao.enums.CanalNotificacao;
import com.limio.api.modulos.notificacao.envionotificacao.EnvioNotificacao;
import com.limio.api.modulos.notificacao.envionotificacao.EnvioNotificacaoService;
import com.limio.api.modulos.notificacao.eventos.NotificacaoRegistradaEvent;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacaoService;

/**
 * UC "Enviar notificações respeitando as preferências" (ETI-112), segunda metade: envia a notificação já gravada só
 * pelos canais que o usuário ligou e registra cada tentativa em {@link EnvioNotificacao}.
 *
 * <ul>
 *   <li><b>Push</b>: pros aparelhos registrados. Sem aparelho, nada a enviar. Token que o Expo diz não existir mais
 *       é apagado.</li>
 *   <li><b>E-mail</b>: pelo Resend, só pra e-mail confirmado.</li>
 *   <li><b>WhatsApp</b>: só com consentimento ativo ({@code AceiteTermo} gravado e {@code whatsapp = true}, decisão
 *       do PO). {@code AceiteTermo} ainda não existe, então ninguém tem consentimento e nada sai por WhatsApp. Sem
 *       provedor escolhido. SMS saiu de escopo no ETI-134.</li>
 * </ul>
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
    private final AparelhoPushService aparelhoPushService;
    private final EnvioNotificacaoService envioNotificacaoService;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final PushExpoService pushExpoService;
    private final EmailAvisoService emailAvisoService;
    private final Clock relogio;

    public EntregarNotificacaoUseCase(NotificacaoService notificacaoService,
            PreferenciaNotificacaoService preferenciaNotificacaoService,
            AparelhoPushService aparelhoPushService,
            EnvioNotificacaoService envioNotificacaoService,
            ConsultaDadosUsuario consultaDadosUsuario,
            PushExpoService pushExpoService,
            EmailAvisoService emailAvisoService,
            Clock relogio) {
        this.notificacaoService = notificacaoService;
        this.preferenciaNotificacaoService = preferenciaNotificacaoService;
        this.aparelhoPushService = aparelhoPushService;
        this.envioNotificacaoService = envioNotificacaoService;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.pushExpoService = pushExpoService;
        this.emailAvisoService = emailAvisoService;
        this.relogio = relogio;
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
        for (CanalNotificacao canal : CanalNotificacao.values()) {
            if (ligado(preferencia, canal)) {
                enviar(notificacao, canal).ifPresent(envios::add);
            }
        }
        envioNotificacaoService.salvarTodos(envios);
    }

    private static boolean ligado(PreferenciaNotificacao preferencia, CanalNotificacao canal) {
        return switch (canal) {
            case PUSH -> preferencia.getPush();
            case EMAIL -> preferencia.getEmail();
            case WHATSAPP -> preferencia.getWhatsapp();
        };
    }

    /** Vazio quando não houve tentativa (sem aparelho, sem e-mail confirmado, sem consentimento do WhatsApp). */
    private Optional<EnvioNotificacao> enviar(Notificacao notificacao, CanalNotificacao canal) {
        try {
            boolean tentou = switch (canal) {
                case PUSH -> enviarPush(notificacao);
                case EMAIL -> enviarEmail(notificacao);
                case WHATSAPP -> false;
            };
            return tentou ? Optional.of(EnvioNotificacao.enviado(notificacao.getId(), canal, relogio.instant()))
                    : Optional.empty();
        } catch (RuntimeException e) {
            log.error("falha ao enviar a notificação {} por {}", notificacao.getId(), canal, e);
            return Optional.of(EnvioNotificacao.falhou(notificacao.getId(), canal, relogio.instant(),
                    e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private boolean enviarPush(Notificacao notificacao) {
        List<String> tokens = aparelhoPushService.tokensDoUsuario(notificacao.getUsuarioId());
        if (tokens.isEmpty()) {
            return false;
        }
        List<String> invalidos = pushExpoService.enviar(tokens, notificacao.getTitulo(), notificacao.getCorpo(),
                dadosDoToque(notificacao));
        aparelhoPushService.apagarTokens(invalidos);
        return true;
    }

    private boolean enviarEmail(Notificacao notificacao) {
        Optional<String> email = consultaDadosUsuario.buscarPorId(notificacao.getUsuarioId())
                .filter(DadosUsuario::emailConfirmado)
                .map(DadosUsuario::email);
        email.ifPresent(destinatario ->
                emailAvisoService.enviar(destinatario, notificacao.getTitulo(), notificacao.getCorpo()));
        return email.isPresent();
    }

    /** O que o app precisa pra abrir a tela certa ao tocar no push (card par [FRONT-END]). */
    private static Map<String, String> dadosDoToque(Notificacao notificacao) {
        Map<String, String> dados = new HashMap<>();
        dados.put("notificacaoId", notificacao.getId().toString());
        dados.put("tipo", notificacao.getTipo().name());
        if (notificacao.getAnuncioId() != null) {
            dados.put("anuncioId", notificacao.getAnuncioId().toString());
        }
        return dados;
    }
}
