package com.limio.api.modulos.chat.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.eventos.PrestadorEscolhidoEvent;
import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatService;
import com.limio.api.modulos.chat.actions.usecase.DeixarChatSomenteLeituraUseCase;
import com.limio.api.modulos.chat.enums.MotivoSomenteLeitura;

@ExtendWith(MockitoExtension.class)
class DeixarChatSomenteLeituraUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-07T15:00:00Z");

    @Mock
    private ChatService chatService;

    private DeixarChatSomenteLeituraUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new DeixarChatSomenteLeituraUseCase(chatService);
    }

    private static Chat chatAberto(UUID aplicacaoId) {
        Chat chat = Chat.builder()
                .aplicacaoId(aplicacaoId)
                .anuncioId(ANUNCIO_ID)
                .contratanteId(UUID.randomUUID())
                .prestadorId(UUID.randomUUID())
                .build();
        ReflectionTestUtils.setField(chat, "id", UUID.randomUUID()); // BaseEntity compara por id
        return chat;
    }

    private static PrestadorEscolhidoEvent evento(UUID escolhida, List<UUID> naoEscolhidas) {
        return new PrestadorEscolhidoEvent(ANUNCIO_ID, "Instalar 3 tomadas", escolhida, UUID.randomUUID(),
                UUID.randomUUID(), naoEscolhidas, AGORA);
    }

    @Test
    void dadoPrestadorEscolhido_quandoReage_entaoSoOsChatsDosNaoEscolhidosFicamSomenteLeitura() {
        UUID escolhida = UUID.randomUUID();
        UUID naoEscolhida1 = UUID.randomUUID();
        UUID naoEscolhida2 = UUID.randomUUID();
        Chat chat1 = chatAberto(naoEscolhida1);
        Chat chat2 = chatAberto(naoEscolhida2);
        when(chatService.buscarDasCandidaturas(List.of(naoEscolhida1, naoEscolhida2)))
                .thenReturn(List.of(chat1, chat2));

        useCase.aoEscolherPrestador(evento(escolhida, List.of(naoEscolhida1, naoEscolhida2)));

        for (Chat chat : List.of(chat1, chat2)) {
            assertThat(chat.isSomenteLeitura()).isTrue();
            assertThat(chat.getMotivoSomenteLeitura()).isEqualTo(MotivoSomenteLeitura.OUTRO_PRESTADOR_ESCOLHIDO);
            assertThat(chat.getSomenteLeituraEm()).isEqualTo(AGORA);
            verify(chatService).salvar(chat);
        }
        // o chat da escolhida nem é consultado
        verify(chatService, never()).buscarDaCandidatura(escolhida);
    }

    @Test
    void dadoEscolhaSemOutrosCandidatos_quandoReage_entaoNaoFazNada() {
        useCase.aoEscolherPrestador(evento(UUID.randomUUID(), List.of()));

        verifyNoInteractions(chatService);
    }

    @Test
    void dadoCandidaturaSemChat_quandoReage_entaoIgnoraSemFalhar() {
        UUID semChat = UUID.randomUUID();
        when(chatService.buscarDasCandidaturas(List.of(semChat))).thenReturn(List.of());

        useCase.aoEscolherPrestador(evento(UUID.randomUUID(), List.of(semChat)));

        verify(chatService, never()).salvar(any());
    }

    @Test
    void dadoChatJaSomenteLeitura_quandoReage_entaoMantemOPrimeiroMotivo() {
        UUID candidatura = UUID.randomUUID();
        Chat jaFechado = chatAberto(candidatura);
        jaFechado.deixarSomenteLeitura(MotivoSomenteLeitura.CANDIDATO_DESISTIU, AGORA.minusSeconds(60));
        when(chatService.buscarDasCandidaturas(List.of(candidatura))).thenReturn(List.of(jaFechado));

        useCase.aoEscolherPrestador(evento(UUID.randomUUID(), List.of(candidatura)));

        assertThat(jaFechado.getMotivoSomenteLeitura()).isEqualTo(MotivoSomenteLeitura.CANDIDATO_DESISTIU);
        assertThat(jaFechado.getSomenteLeituraEm()).isEqualTo(AGORA.minusSeconds(60));
    }
}
