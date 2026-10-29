package com.limio.api.modulos.chat.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.eventos.AnuncioCanceladoEvent;
import com.limio.api.comum.eventos.CandidaturaCriadaEvent;
import com.limio.api.comum.eventos.ContaEncerradaEvent;
import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatService;
import com.limio.api.modulos.chat.actions.usecase.CriarChatDaCandidaturaUseCase;
import com.limio.api.modulos.chat.actions.usecase.DeixarChatSomenteLeituraUseCase;
import com.limio.api.modulos.chat.enums.MotivoSomenteLeitura;

/**
 * Ciclo de vida do chat: nasce com a candidatura e fica somente leitura quando ela, o anúncio ou a conta de uma das
 * partes encerram.
 */
@ExtendWith(MockitoExtension.class)
class ChatSomenteLeituraTest {

    private static final UUID APLICACAO_ID = UUID.randomUUID();
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();

    @Mock
    private ChatService chatService;

    private CriarChatDaCandidaturaUseCase criar;
    private DeixarChatSomenteLeituraUseCase deixarSomenteLeitura;

    @BeforeEach
    void montarUseCases() {
        criar = new CriarChatDaCandidaturaUseCase(chatService);
        deixarSomenteLeitura = new DeixarChatSomenteLeituraUseCase(chatService);
    }

    private static Chat chat() {
        return Chat.builder()
                .aplicacaoId(APLICACAO_ID)
                .anuncioId(ANUNCIO_ID)
                .contratanteId(CONTRATANTE_ID)
                .prestadorId(PRESTADOR_ID)
                .build();
    }

    @Test
    void dadaCandidaturaCriada_quandoChegaOEvento_entaoGaranteOChatComAsDuasPartes() {
        criar.aoCriarCandidatura(new CandidaturaCriadaEvent(APLICACAO_ID, ANUNCIO_ID, "Faxina", CONTRATANTE_ID,
                PRESTADOR_ID, Instant.now()));

        verify(chatService).garantirDaCandidatura(APLICACAO_ID, ANUNCIO_ID, CONTRATANTE_ID, PRESTADOR_ID);
    }

    @Test
    void dadoAnuncioCancelado_quandoChegaOEvento_entaoTodosOsChatsAbertosFicamSomenteLeitura() {
        Chat um = chat();
        Chat outro = chat();
        when(chatService.buscarAbertosDoAnuncio(ANUNCIO_ID)).thenReturn(List.of(um, outro));
        Instant agora = Instant.now();

        deixarSomenteLeitura.aoCancelarAnuncio(new AnuncioCanceladoEvent(ANUNCIO_ID, agora));

        assertThat(List.of(um, outro)).allSatisfy(chat -> {
            assertThat(chat.isSomenteLeitura()).isTrue();
            assertThat(chat.getMotivoSomenteLeitura()).isEqualTo(MotivoSomenteLeitura.ANUNCIO_CANCELADO);
            assertThat(chat.getSomenteLeituraEm()).isEqualTo(agora);
        });
        // sem id as duas entidades são iguais pelo equals da BaseEntity: conta as gravações, não a instância
        verify(chatService, times(2)).salvar(any());
    }

    @Test
    void dadaContaEncerrada_quandoChegaOEvento_entaoChatsAbertosDaPessoaFicamSomenteLeitura() {
        Chat chat = chat();
        when(chatService.buscarAbertosDoParticipante(PRESTADOR_ID)).thenReturn(List.of(chat));
        Instant agora = Instant.now();

        deixarSomenteLeitura.aoEncerrarConta(new ContaEncerradaEvent(PRESTADOR_ID, agora));

        assertThat(chat.isSomenteLeitura()).isTrue();
        assertThat(chat.getMotivoSomenteLeitura()).isEqualTo(MotivoSomenteLeitura.CONTA_ENCERRADA);
        assertThat(chat.getSomenteLeituraEm()).isEqualTo(agora);
        verify(chatService).salvar(chat);
    }

    @Test
    void dadoChatJaSomenteLeitura_quandoChegaOutroMotivo_entaoPrimeiroMotivoFica() {
        Chat chat = chat();
        Instant recusa = Instant.now().minusSeconds(60);
        chat.deixarSomenteLeitura(MotivoSomenteLeitura.CANDIDATURA_RECUSADA, recusa);
        when(chatService.buscarDaCandidatura(APLICACAO_ID)).thenReturn(Optional.of(chat));

        deixarSomenteLeitura.executarDaCandidatura(APLICACAO_ID, MotivoSomenteLeitura.ANUNCIO_CANCELADO, Instant.now());

        assertThat(chat.getMotivoSomenteLeitura()).isEqualTo(MotivoSomenteLeitura.CANDIDATURA_RECUSADA);
        assertThat(chat.getSomenteLeituraEm()).isEqualTo(recusa);
    }

    @Test
    void dadaCandidaturaSemChat_quandoEncerra_entaoNadaAFazer() {
        when(chatService.buscarDaCandidatura(APLICACAO_ID)).thenReturn(Optional.empty());

        deixarSomenteLeitura.executarDaCandidatura(APLICACAO_ID, MotivoSomenteLeitura.CANDIDATURA_RECUSADA,
                Instant.now());

        verify(chatService, never()).salvar(any());
    }
}
