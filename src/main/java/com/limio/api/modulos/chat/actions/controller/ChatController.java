package com.limio.api.modulos.chat.actions.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.chat.actions.mapper.ChatMapper;
import com.limio.api.modulos.chat.actions.usecase.AbrirChatUseCase;
import com.limio.api.modulos.chat.actions.usecase.ConfirmarMensagensUseCase;
import com.limio.api.modulos.chat.actions.usecase.EnviarMensagemUseCase;
import com.limio.api.modulos.chat.enums.Confirmacao;
import com.limio.api.modulos.chat.records.ChatResponse;
import com.limio.api.modulos.chat.records.EnviarMensagemRequest;
import com.limio.api.modulos.chat.records.HistoricoFiltro;
import com.limio.api.modulos.chat.records.MensagemResponse;

import jakarta.validation.Valid;

/**
 * Chat 1:1 da candidatura (ETI-101). Não há checagem de papel: quem decide o acesso é ser uma das duas partes do chat,
 * e para qualquer outra pessoa a resposta é 404 {@code CHAT_NAO_ENCONTRADO}. Mensagens novas, entregas e leituras
 * chegam em tempo real em {@code /user/queue/chat} (STOMP em {@code /ws}).
 */
@RestController
public class ChatController {

    private final AbrirChatUseCase abrirChatUseCase;
    private final EnviarMensagemUseCase enviarMensagemUseCase;
    private final ConfirmarMensagensUseCase confirmarMensagensUseCase;
    private final ChatMapper chatMapper;

    public ChatController(AbrirChatUseCase abrirChatUseCase, EnviarMensagemUseCase enviarMensagemUseCase,
            ConfirmarMensagensUseCase confirmarMensagensUseCase, ChatMapper chatMapper) {
        this.abrirChatUseCase = abrirChatUseCase;
        this.enviarMensagemUseCase = enviarMensagemUseCase;
        this.confirmarMensagensUseCase = confirmarMensagensUseCase;
        this.chatMapper = chatMapper;
    }

    @GetMapping("/candidaturas/{candidaturaId}/chat")
    public ResponseEntity<ChatResponse> abrirChat(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID candidaturaId, @Valid @ModelAttribute HistoricoFiltro filtro) {
        return ResponseEntity.ok(chatMapper.toResponse(
                abrirChatUseCase.executar(candidaturaId, usuario.id(), filtro.antesDe(), filtro.tamanho())));
    }

    @PostMapping("/chats/{chatId}/mensagens")
    public ResponseEntity<MensagemResponse> enviarMensagem(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID chatId, @Valid @RequestBody EnviarMensagemRequest request) {
        var mensagem = enviarMensagemUseCase.executar(chatId, usuario.id(), request.texto());
        return ResponseEntity.status(HttpStatus.CREATED).body(chatMapper.toResponse(mensagem));
    }

    /** O app chama ao receber mensagens (pelo canal ou ao abrir o chat). */
    @PostMapping("/chats/{chatId}/entregues")
    public ResponseEntity<Void> marcarEntregues(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID chatId) {
        confirmarMensagensUseCase.executar(chatId, usuario.id(), Confirmacao.ENTREGUE);
        return ResponseEntity.noContent().build();
    }

    /** O app chama com a conversa na tela. */
    @PostMapping("/chats/{chatId}/lidas")
    public ResponseEntity<Void> marcarLidas(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID chatId) {
        confirmarMensagensUseCase.executar(chatId, usuario.id(), Confirmacao.LIDA);
        return ResponseEntity.noContent().build();
    }
}
