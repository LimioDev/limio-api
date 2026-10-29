package com.limio.api.modulos.chat.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.chat.ChatAberto;
import com.limio.api.modulos.chat.OutraParte;
import com.limio.api.modulos.chat.enums.TipoEventoChat;
import com.limio.api.modulos.chat.eventos.MensagemEnviadaEvent;
import com.limio.api.modulos.chat.eventos.MensagensConfirmadasEvent;
import com.limio.api.modulos.chat.mensagem.Mensagem;
import com.limio.api.modulos.chat.records.ChatResponse;
import com.limio.api.modulos.chat.records.EventoChatResponse;
import com.limio.api.modulos.chat.records.MensagemResponse;

/**
 * Chat e mensagens para os contratos HTTP e do canal em tempo real. O canal ({@code ChatTempoRealService}) é saída
 * igual ao controller, por isso também usa este mapper: exceção registrada à regra "mapper só no controller" (ver
 * {@code docs/history/CHANGELOG.md}).
 */
@Component
public class ChatMapper {

    public ChatResponse toResponse(ChatAberto aberto) {
        OutraParte outraParte = aberto.outraParte();
        return new ChatResponse(
                aberto.chat().getId(),
                aberto.chat().getAplicacaoId(),
                aberto.candidatura().status(),
                new ChatResponse.OutraParte(outraParte.nome(), outraParte.fotoUrl(), outraParte.fotoExpiraEm(),
                        outraParte.papel()),
                new ChatResponse.Anuncio(aberto.candidatura().anuncioTitulo(), aberto.candidatura().categoriaNome()),
                aberto.chat().isSomenteLeitura(),
                aberto.chat().getMotivoSomenteLeitura(),
                new ChatResponse.Historico(aberto.mensagens().stream().map(this::toResponse).toList(),
                        aberto.tamanho(), aberto.temMais()));
    }

    public MensagemResponse toResponse(Mensagem mensagem) {
        return new MensagemResponse(
                mensagem.getId(),
                mensagem.getChatId(),
                mensagem.getRemetenteId(),
                mensagem.getTextoExibido(),
                mensagem.getDataEnvio(),
                mensagem.getEntregueEm(),
                mensagem.getLidaEm());
    }

    public EventoChatResponse toEvento(MensagemEnviadaEvent evento) {
        Mensagem mensagem = evento.mensagem();
        return new EventoChatResponse(TipoEventoChat.MENSAGEM_NOVA, mensagem.getChatId(), toResponse(mensagem), null,
                mensagem.getDataEnvio());
    }

    public EventoChatResponse toEvento(MensagensConfirmadasEvent evento) {
        TipoEventoChat tipo = switch (evento.confirmacao()) {
            case ENTREGUE -> TipoEventoChat.ENTREGUE;
            case LIDA -> TipoEventoChat.LIDA;
        };
        return new EventoChatResponse(tipo, evento.chatId(), null, evento.mensagemIds(), evento.em());
    }
}
