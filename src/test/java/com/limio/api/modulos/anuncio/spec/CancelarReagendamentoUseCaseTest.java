package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.eventos.ReagendamentoCanceladoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.CancelarReagendamentoUseCase;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.excecao.PropostaEncerradaException;
import com.limio.api.modulos.anuncio.excecao.PropostaExpiradaException;
import com.limio.api.modulos.anuncio.excecao.ReagendamentoNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.SomenteProponenteCancelaException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/** O proponente cancela a própria proposta (ETI-99): quem pode, quando pode e o que fica registrado. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CancelarReagendamentoUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID REAGENDAMENTO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID ESTRANHO_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-11T15:00:00Z");
    private static final Instant DATA_VIGENTE = AGORA.plus(Duration.ofDays(3));
    private static final String IP = "203.0.113.7";

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ReagendamentoService reagendamentoService;

    @Mock
    private LogAuditoriaService logAuditoriaService;

    @Mock
    private ApplicationEventPublisher eventos;

    @Mock
    private TravaTransacional trava;

    private CancelarReagendamentoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new CancelarReagendamentoUseCase(anuncioService, aplicacaoService, reagendamentoService,
                logAuditoriaService, eventos, trava, Clock.fixed(AGORA, ZoneOffset.UTC));
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .dataHoraPretendida(DATA_VIGENTE)
                .status(StatusAnuncio.PUBLICADO)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.buscarPrestadorEscolhido(ANUNCIO_ID)).thenReturn(Optional.of(PRESTADOR_ID));
        when(reagendamentoService.buscarAnuncioId(REAGENDAMENTO_ID)).thenReturn(Optional.of(ANUNCIO_ID));
        when(reagendamentoService.salvar(any(Reagendamento.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---------------------------------------------------------------- cenário

    /** Proposta {@code PENDENTE} do {@code proponenteId}, com 24h de prazo a partir de AGORA. */
    private Reagendamento pendenteDe(UUID proponenteId) {
        Reagendamento proposta = Reagendamento.propor(ANUNCIO_ID, proponenteId, DATA_VIGENTE,
                DATA_VIGENTE.plus(Duration.ofDays(2)), "Imprevisto", AGORA.plus(Duration.ofHours(24)), false);
        ReflectionTestUtils.setField(proposta, "id", REAGENDAMENTO_ID);
        when(reagendamentoService.buscarPorId(REAGENDAMENTO_ID)).thenReturn(Optional.of(proposta));
        return proposta;
    }

    private void verificarNadaMudou() {
        verify(reagendamentoService, never()).salvar(any());
        verifyNoInteractions(logAuditoriaService, eventos);
    }

    // ---------------------------------------------------------------- caminho feliz

    @Test
    void dadaPropostaPendenteDoEmpregador_quandoEleCancela_entaoFicaCanceladaComOInstanteDaResposta() {
        Reagendamento proposta = pendenteDe(CONTRATANTE_ID);

        Reagendamento cancelada = useCase.executar(CONTRATANTE_ID, REAGENDAMENTO_ID, IP);

        assertThat(cancelada).isSameAs(proposta);
        assertThat(cancelada.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
        assertThat(cancelada.getRespondidoEm()).isEqualTo(AGORA);
        // a data vigente é do anúncio e nada a altera: cancelar nem toca no anúncio
        verify(anuncioService, never()).salvar(any());
        verify(reagendamentoService).salvar(proposta);
    }

    @Test
    void dadaPropostaPendenteDoPrestador_quandoEleCancela_entaoFicaCancelada() {
        pendenteDe(PRESTADOR_ID);

        Reagendamento cancelada = useCase.executar(PRESTADOR_ID, REAGENDAMENTO_ID, IP);

        assertThat(cancelada.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
    }

    @Test
    void dadaProposta_quandoCancela_entaoTravaOAnuncioAntesDeLerAProposta() {
        pendenteDe(CONTRATANTE_ID);

        useCase.executar(CONTRATANTE_ID, REAGENDAMENTO_ID, IP);

        InOrder ordem = inOrder(reagendamentoService, trava);
        ordem.verify(reagendamentoService).buscarAnuncioId(REAGENDAMENTO_ID);
        ordem.verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());
        ordem.verify(reagendamentoService).buscarPorId(REAGENDAMENTO_ID);
    }

    @Test
    void dadaProposta_quandoCancela_entaoAuditaEPublicaEventoParaAContraparte() {
        pendenteDe(CONTRATANTE_ID);

        useCase.executar(CONTRATANTE_ID, REAGENDAMENTO_ID, IP);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> detalhes = ArgumentCaptor.forClass(Map.class);
        verify(logAuditoriaService).registrar(eq("Reagendamento"), eq(REAGENDAMENTO_ID),
                eq(TipoAcaoAuditoria.REAGENDAMENTO_CANCELADO), anyString(), eq(IP), detalhes.capture());
        assertThat(detalhes.getValue())
                .containsEntry("anuncioId", ANUNCIO_ID.toString())
                .containsEntry("proponenteId", CONTRATANTE_ID.toString())
                .containsEntry("dataAnterior", DATA_VIGENTE.toString());

        ArgumentCaptor<ReagendamentoCanceladoEvent> evento = ArgumentCaptor.forClass(ReagendamentoCanceladoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().reagendamentoId()).isEqualTo(REAGENDAMENTO_ID);
        assertThat(evento.getValue().contratanteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().prestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(evento.getValue().proponenteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().ocorridoEm()).isEqualTo(AGORA);
    }

    // ---------------------------------------------------------------- autorização

    @Test
    void dadaPropostaDoPrestador_quandoOEmpregadorTentaCancelar_entao403SemEfeito() {
        Reagendamento proposta = pendenteDe(PRESTADOR_ID);

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, REAGENDAMENTO_ID, IP))
                .isInstanceOf(SomenteProponenteCancelaException.class);

        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
        verificarNadaMudou();
    }

    @Test
    void dadaPropostaDoEmpregador_quandoOPrestadorTentaCancelar_entao403SemEfeito() {
        Reagendamento proposta = pendenteDe(CONTRATANTE_ID);

        assertThatThrownBy(() -> useCase.executar(PRESTADOR_ID, REAGENDAMENTO_ID, IP))
                .isInstanceOf(SomenteProponenteCancelaException.class);

        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
        verificarNadaMudou();
    }

    @Test
    void dadoUsuarioQueNaoEParteDoServico_quandoTentaCancelar_entao404SemEfeito() {
        pendenteDe(CONTRATANTE_ID);

        assertThatThrownBy(() -> useCase.executar(ESTRANHO_ID, REAGENDAMENTO_ID, IP))
                .isInstanceOf(ReagendamentoNaoEncontradoException.class);

        verificarNadaMudou();
    }

    @Test
    void dadaPropostaInexistente_quandoTentaCancelar_entao404SemTravarNada() {
        when(reagendamentoService.buscarAnuncioId(REAGENDAMENTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, REAGENDAMENTO_ID, IP))
                .isInstanceOf(ReagendamentoNaoEncontradoException.class);

        verifyNoInteractions(trava);
        verificarNadaMudou();
    }

    // ---------------------------------------------------------------- estado e prazo

    @ParameterizedTest
    @EnumSource(value = StatusReagendamento.class, names = "PENDENTE", mode = EnumSource.Mode.EXCLUDE)
    void dadaPropostaJaEncerrada_quandoOProponenteTentaCancelar_entao409SemEfeito(StatusReagendamento status) {
        Reagendamento proposta = pendenteDe(CONTRATANTE_ID);
        ReflectionTestUtils.setField(proposta, "status", status);

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, REAGENDAMENTO_ID, IP))
                .isInstanceOf(PropostaEncerradaException.class);

        assertThat(proposta.getStatus()).isEqualTo(status);
        verificarNadaMudou();
    }

    @Test
    void dadaPropostaPendenteQuePassouDoPrazo_quandoOProponenteTentaCancelar_entao409SemEfeito() {
        Reagendamento proposta = pendenteDe(CONTRATANTE_ID);
        ReflectionTestUtils.setField(proposta, "expiraEm", AGORA);

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, REAGENDAMENTO_ID, IP))
                .isInstanceOf(PropostaExpiradaException.class);

        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
        verificarNadaMudou();
    }

    @Test
    void dadaPropostaAHUmMilissegundoDoPrazo_quandoOProponenteCancela_entaoPermite() {
        Reagendamento proposta = pendenteDe(CONTRATANTE_ID);
        ReflectionTestUtils.setField(proposta, "expiraEm", AGORA.plusMillis(1));

        Reagendamento cancelada = useCase.executar(CONTRATANTE_ID, REAGENDAMENTO_ID, IP);

        assertThat(cancelada.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
    }
}
