package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.eventos.PrestadorEscolhidoEvent;
import com.limio.api.comum.eventos.ServicoConcluidoEvent;
import com.limio.api.comum.eventos.enums.OrigemConclusao;
import com.limio.api.comum.portas.ConsultaCandidatura;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.NotificacaoService;
import com.limio.api.modulos.notificacao.actions.usecase.RegistrarAvisosUseCase;
import com.limio.api.modulos.notificacao.enums.TipoNotificacao;
import com.limio.api.modulos.notificacao.eventos.NotificacaoRegistradaEvent;

@ExtendWith(MockitoExtension.class)
class RegistrarAvisosUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-16T15:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Mock
    private NotificacaoService notificacaoService;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ConsultaCandidatura consultaCandidatura;

    @Mock
    private ApplicationEventPublisher eventos;

    @Test
    void dadoEscolha_quandoRegistra_entaoAvisaOEscolhidoECadaNaoEscolhidoNumaConsultaSo() {
        UUID escolhido = UUID.randomUUID();
        UUID naoEscolhido1 = UUID.randomUUID();
        UUID naoEscolhido2 = UUID.randomUUID();
        List<UUID> candidaturasNaoEscolhidas = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(consultaCandidatura.buscarPrestadoresIds(candidaturasNaoEscolhidas))
                .thenReturn(List.of(naoEscolhido1, naoEscolhido2));
        when(notificacaoService.salvar(any())).thenAnswer(chamada -> comId(chamada.getArgument(0)));
        RegistrarAvisosUseCase useCase = new RegistrarAvisosUseCase(notificacaoService, consultaDadosUsuario,
                consultaCandidatura, eventos, RELOGIO);

        useCase.aoEscolherPrestador(new PrestadorEscolhidoEvent(ANUNCIO_ID, "Troca", UUID.randomUUID(), escolhido,
                UUID.randomUUID(), candidaturasNaoEscolhidas, AGORA));

        ArgumentCaptor<Notificacao> salvas = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoService, times(3)).salvar(salvas.capture());
        assertThat(salvas.getAllValues()).extracting(Notificacao::getUsuarioId, Notificacao::getTipo)
                .containsExactly(
                        tuple(escolhido, TipoNotificacao.PRESTADOR_ESCOLHIDO),
                        tuple(naoEscolhido1, TipoNotificacao.CANDIDATURA_ENCERRADA),
                        tuple(naoEscolhido2, TipoNotificacao.CANDIDATURA_ENCERRADA));
        verify(eventos, times(3)).publishEvent(any(NotificacaoRegistradaEvent.class));
        verify(consultaCandidatura, times(1)).buscarPrestadoresIds(candidaturasNaoEscolhidas);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "VALIDACAO | 'Troca' foi concluído. Avalie a outra parte.",
            "SILENCIO | 'Troca' foi concluído automaticamente. Avalie a outra parte." })
    void dadaConclusao_quandoRegistra_entaoAvisaAsDuasPartesComOTextoDaOrigem(OrigemConclusao origem, String texto) {
        UUID contratante = UUID.randomUUID();
        UUID prestador = UUID.randomUUID();
        when(notificacaoService.salvar(any())).thenAnswer(chamada -> comId(chamada.getArgument(0)));
        RegistrarAvisosUseCase useCase = new RegistrarAvisosUseCase(notificacaoService, consultaDadosUsuario,
                consultaCandidatura, eventos, RELOGIO);

        useCase.aoConcluirServico(
                new ServicoConcluidoEvent(ANUNCIO_ID, "Troca", contratante, prestador, origem, AGORA));

        ArgumentCaptor<Notificacao> salvas = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoService, times(2)).salvar(salvas.capture());
        assertThat(salvas.getAllValues())
                .extracting(Notificacao::getUsuarioId, Notificacao::getTipo, Notificacao::getCorpo)
                .containsExactly(
                        tuple(contratante, TipoNotificacao.SERVICO_CONCLUIDO, texto),
                        tuple(prestador, TipoNotificacao.SERVICO_CONCLUIDO, texto));
    }

    private static Notificacao comId(Notificacao notificacao) {
        ReflectionTestUtils.setField(notificacao, "id", UUID.randomUUID());
        return notificacao;
    }
}
