package com.limio.api.modulos.chat.actions.usecase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatService;
import com.limio.api.modulos.chat.enums.Confirmacao;
import com.limio.api.modulos.chat.eventos.MensagensConfirmadasEvent;
import com.limio.api.modulos.chat.mensagem.Mensagem;
import com.limio.api.modulos.chat.mensagem.MensagemService;

/**
 * Quem recebeu confirma as mensagens da outra parte (ETI-101): entregues quando chegam ao aparelho, lidas quando a
 * conversa é aberta. Vale também em chat somente leitura. Cada data é preenchida uma vez; chamada repetida não muda
 * nada e não avisa ninguém. O aviso ao remetente sai em tempo real depois do commit.
 */
@Service
public class ConfirmarMensagensUseCase {

    private final ChatService chatService;
    private final MensagemService mensagemService;
    private final ApplicationEventPublisher eventos;

    public ConfirmarMensagensUseCase(ChatService chatService, MensagemService mensagemService,
            ApplicationEventPublisher eventos) {
        this.chatService = chatService;
        this.mensagemService = mensagemService;
        this.eventos = eventos;
    }

    @Transactional
    public void executar(UUID chatId, UUID usuarioId, Confirmacao confirmacao) {
        Chat chat = chatService.buscarDoParticipante(chatId, usuarioId);
        Instant agora = Instant.now();
        List<Mensagem> mensagens = switch (confirmacao) {
            case ENTREGUE -> mensagemService.buscarNaoEntreguesPara(chatId, usuarioId);
            case LIDA -> mensagemService.buscarNaoLidasPor(chatId, usuarioId);
        };
        if (mensagens.isEmpty())
            return;
        for (Mensagem mensagem : mensagens) {
            switch (confirmacao) {
                case ENTREGUE -> mensagem.marcarEntregue(agora);
                case LIDA -> mensagem.marcarLida(agora);
            }
        }
        mensagemService.salvarTodas(mensagens);
        eventos.publishEvent(new MensagensConfirmadasEvent(chatId, chat.outraParte(usuarioId), confirmacao,
                mensagens.stream().map(Mensagem::getId).toList(), agora));
    }
}
