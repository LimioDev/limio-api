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
import com.limio.api.comum.eventos.ReagendamentoAceitoEvent;
import com.limio.api.comum.eventos.ReagendamentoRecusadoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.ResponderReagendamentoUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.DecisaoReagendamento;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.excecao.DataNoPassadoException;
import com.limio.api.modulos.anuncio.excecao.ProponenteNaoRespondeException;
import com.limio.api.modulos.anuncio.excecao.PropostaEncerradaException;
import com.limio.api.modulos.anuncio.excecao.PropostaExpiradaException;
import com.limio.api.modulos.anuncio.excecao.ReagendamentoNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/** Aceitar ou recusar a proposta (ETI-99, US47): as validações, a ordem delas e os efeitos de cada decisão. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResponderReagendamentoUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID REAGENDAMENTO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID ESTRANHO_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-11T15:00:00Z");
    private static final Instant DATA_VIGENTE = AGORA.plus(Duration.ofDays(3));
    private static final Instant DATA_PROPOSTA = AGORA.plus(Duration.ofDays(5));
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

    private ResponderReagendamentoUseCase useCase;
    private Anuncio anuncio;
    private Reagendamento proposta;

    @BeforeEach
    void montarUseCase() {
        useCase = new ResponderReagendamentoUseCase(anuncioService, aplicacaoService, reagendamentoService,
                logAuditoriaService, eventos, trava, Clock.fixed(AGORA, ZoneOffset.UTC));
        anuncio = anuncio(StatusAnuncio.PUBLICADO);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(anuncioService.salvar(any(Anuncio.class))).thenAnswer(i -> i.getArgument(0));
        when(aplicacaoService.buscarPrestadorDoServico(ANUNCIO_ID)).thenReturn(Optional.of(PRESTADOR_ID));
        when(aplicacaoService.buscarEscolhidaDoAnuncioParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));
        when(reagendamentoService.buscarAnuncioId(REAGENDAMENTO_ID)).thenReturn(Optional.of(ANUNCIO_ID));
        when(reagendamentoService.salvar(any(Reagendamento.class))).thenAnswer(i -> i.getArgument(0));
        proposta = pendenteDe(CONTRATANTE_ID);
    }

    // ---------------------------------------------------------------- cenário

    private Anuncio anuncio(StatusAnuncio status) {
        Anuncio novo = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .dataHoraPretendida(DATA_VIGENTE)
                .status(status)
                .build();
        ReflectionTestUtils.setField(novo, "id", ANUNCIO_ID);
        return novo;
    }

    private static Aplicacao escolhida() {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(ANUNCIO_ID)
                .prestadorId(PRESTADOR_ID)
                .status(StatusAplicacao.ESCOLHIDA)
                .dataAplicacao(AGORA.minus(Duration.ofDays(2)))
                .escolhidaEm(AGORA.minus(Duration.ofDays(1)))
                .build();
        ReflectionTestUtils.setField(aplicacao, "id", UUID.randomUUID());
        return aplicacao;
    }

    /** Proposta {@code PENDENTE} do {@code proponenteId}, com 24h de prazo a partir de AGORA. */
    private Reagendamento pendenteDe(UUID proponenteId) {
        Reagendamento nova = Reagendamento.propor(ANUNCIO_ID, proponenteId, DATA_VIGENTE, DATA_PROPOSTA, "Imprevisto",
                AGORA.plus(Duration.ofHours(24)), false);
        ReflectionTestUtils.setField(nova, "id", REAGENDAMENTO_ID);
        when(reagendamentoService.buscarPorId(REAGENDAMENTO_ID)).thenReturn(Optional.of(nova));
        return nova;
    }

    private Reagendamento responder(UUID usuarioId, DecisaoReagendamento decisao) {
        return useCase.executar(usuarioId, REAGENDAMENTO_ID, decisao, IP);
    }

    private void verificarNadaMudou() {
        verify(reagendamentoService, never()).salvar(any());
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(logAuditoriaService, eventos);
    }

    // ---------------------------------------------------------------- aceitar

    @Test
    void dadaPropostaDoEmpregador_quandoOPrestadorAceita_entaoADataVigenteMudaEAPropostaFicaAceita() {
        Reagendamento aceita = responder(PRESTADOR_ID, DecisaoReagendamento.ACEITAR);

        assertThat(aceita.getStatus()).isEqualTo(StatusReagendamento.ACEITO);
        assertThat(aceita.getRespondidoEm()).isEqualTo(AGORA);
        assertThat(anuncio.getDataHoraPretendida()).isEqualTo(DATA_PROPOSTA);
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        verify(anuncioService).salvar(anuncio);
        verify(reagendamentoService).salvar(proposta);
    }

    @Test
    void dadaPropostaDoPrestador_quandoOEmpregadorAceita_entaoADataVigenteMuda() {
        proposta = pendenteDe(PRESTADOR_ID);

        responder(CONTRATANTE_ID, DecisaoReagendamento.ACEITAR);

        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.ACEITO);
        assertThat(anuncio.getDataHoraPretendida()).isEqualTo(DATA_PROPOSTA);
    }

    @Test
    void dadoAceite_quandoResponde_entaoTravaOAnuncioAntesDeLerAProposta() {
        responder(PRESTADOR_ID, DecisaoReagendamento.ACEITAR);

        InOrder ordem = inOrder(reagendamentoService, trava);
        ordem.verify(reagendamentoService).buscarAnuncioId(REAGENDAMENTO_ID);
        ordem.verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());
        ordem.verify(reagendamentoService).buscarPorId(REAGENDAMENTO_ID);
    }

    @Test
    void dadoAceite_quandoResponde_entaoAuditaComADataAnteriorENovaEPublicaEvento() {
        responder(PRESTADOR_ID, DecisaoReagendamento.ACEITAR);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> detalhes = ArgumentCaptor.forClass(Map.class);
        verify(logAuditoriaService).registrar(eq("Reagendamento"), eq(REAGENDAMENTO_ID),
                eq(TipoAcaoAuditoria.REAGENDAMENTO_ACEITO), anyString(), eq(IP), detalhes.capture());
        assertThat(detalhes.getValue())
                .containsEntry("anuncioId", ANUNCIO_ID.toString())
                .containsEntry("proponenteId", CONTRATANTE_ID.toString())
                .containsEntry("respondenteId", PRESTADOR_ID.toString())
                .containsEntry("dataAnterior", DATA_VIGENTE.toString())
                .containsEntry("dataNova", DATA_PROPOSTA.toString())
                .containsEntry("aceitoEm", AGORA.toString());

        ArgumentCaptor<ReagendamentoAceitoEvent> evento = ArgumentCaptor.forClass(ReagendamentoAceitoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().reagendamentoId()).isEqualTo(REAGENDAMENTO_ID);
        assertThat(evento.getValue().contratanteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().prestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(evento.getValue().proponenteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().dataAnterior()).isEqualTo(DATA_VIGENTE);
        assertThat(evento.getValue().dataNova()).isEqualTo(DATA_PROPOSTA);
        assertThat(evento.getValue().ocorridoEm()).isEqualTo(AGORA);
    }

    @Test
    void dadaPropostaDentroDoPrazo_quandoAceitaAUmMilissegundoDoVencimento_entaoPermite() {
        ReflectionTestUtils.setField(proposta, "expiraEm", AGORA.plusMillis(1));

        assertThat(responder(PRESTADOR_ID, DecisaoReagendamento.ACEITAR).getStatus())
                .isEqualTo(StatusReagendamento.ACEITO);
    }

    @Test
    void dadaDataProposta_quandoAceitaComADataJaNoPassado_entao422SemEfeito() {
        ReflectionTestUtils.setField(proposta, "dataProposta", AGORA.minusSeconds(1));

        assertThatThrownBy(() -> responder(PRESTADOR_ID, DecisaoReagendamento.ACEITAR))
                .isInstanceOf(DataNoPassadoException.class);

        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
        assertThat(anuncio.getDataHoraPretendida()).isEqualTo(DATA_VIGENTE);
        verificarNadaMudou();
    }

    @Test
    void dadaDataPropostaIgualAAgora_quandoAceita_entao422SemEfeito() {
        ReflectionTestUtils.setField(proposta, "dataProposta", AGORA);

        assertThatThrownBy(() -> responder(PRESTADOR_ID, DecisaoReagendamento.ACEITAR))
                .isInstanceOf(DataNoPassadoException.class);

        verificarNadaMudou();
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = "PUBLICADO", mode = EnumSource.Mode.EXCLUDE)
    void dadoServicoQueDeixouDeEstarCombinado_quandoAceita_entao409SemEfeito(StatusAnuncio status) {
        anuncio = anuncio(status);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));

        assertThatThrownBy(() -> responder(PRESTADOR_ID, DecisaoReagendamento.ACEITAR))
                .isInstanceOf(ServicoEstadoInvalidoException.class);

        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
        assertThat(anuncio.getDataHoraPretendida()).isEqualTo(DATA_VIGENTE);
        verificarNadaMudou();
    }

    @Test
    void dadoAnuncioPublicadoSemCandidaturaEscolhida_quandoAceita_entao409SemEfeito() {
        when(aplicacaoService.buscarEscolhidaDoAnuncioParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> responder(PRESTADOR_ID, DecisaoReagendamento.ACEITAR))
                .isInstanceOf(ServicoEstadoInvalidoException.class);

        verificarNadaMudou();
    }

    // ---------------------------------------------------------------- recusar

    @Test
    void dadaPropostaPendente_quandoAContrapartRecusa_entaoFicaRecusadaEADataOriginalContinuaVigente() {
        Reagendamento recusada = responder(PRESTADOR_ID, DecisaoReagendamento.RECUSAR);

        assertThat(recusada.getStatus()).isEqualTo(StatusReagendamento.RECUSADO);
        assertThat(recusada.getRespondidoEm()).isEqualTo(AGORA);
        assertThat(anuncio.getDataHoraPretendida()).isEqualTo(DATA_VIGENTE);
        verify(anuncioService, never()).salvar(any());
        verify(reagendamentoService).salvar(proposta);
    }

    @Test
    void dadaRecusa_quandoResponde_entaoAuditaEPublicaEventoParaOProponente() {
        responder(PRESTADOR_ID, DecisaoReagendamento.RECUSAR);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> detalhes = ArgumentCaptor.forClass(Map.class);
        verify(logAuditoriaService).registrar(eq("Reagendamento"), eq(REAGENDAMENTO_ID),
                eq(TipoAcaoAuditoria.REAGENDAMENTO_RECUSADO), anyString(), eq(IP), detalhes.capture());
        assertThat(detalhes.getValue())
                .containsEntry("proponenteId", CONTRATANTE_ID.toString())
                .containsEntry("respondenteId", PRESTADOR_ID.toString())
                .containsEntry("dataVigente", DATA_VIGENTE.toString());

        ArgumentCaptor<ReagendamentoRecusadoEvent> evento = ArgumentCaptor.forClass(ReagendamentoRecusadoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().proponenteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().dataVigente()).isEqualTo(DATA_VIGENTE);
        assertThat(evento.getValue().dataProposta()).isEqualTo(DATA_PROPOSTA);
    }

    @Test
    void dadaDataPropostaJaNoPassado_quandoRecusa_entaoPermite() {
        ReflectionTestUtils.setField(proposta, "dataProposta", AGORA.minusSeconds(1));

        assertThat(responder(PRESTADOR_ID, DecisaoReagendamento.RECUSAR).getStatus())
                .isEqualTo(StatusReagendamento.RECUSADO);
    }

    // ---------------------------------------------------------------- autorização (ambas as decisões)

    @ParameterizedTest
    @EnumSource(DecisaoReagendamento.class)
    void dadaPropostaPropria_quandoOProponenteTentaResponder_entao403SemEfeito(DecisaoReagendamento decisao) {
        assertThatThrownBy(() -> responder(CONTRATANTE_ID, decisao)).isInstanceOf(ProponenteNaoRespondeException.class);

        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
        verificarNadaMudou();
    }

    @ParameterizedTest
    @EnumSource(DecisaoReagendamento.class)
    void dadoUsuarioQueNaoEParteDoServico_quandoTentaResponder_entao404SemEfeito(DecisaoReagendamento decisao) {
        assertThatThrownBy(() -> responder(ESTRANHO_ID, decisao)).isInstanceOf(ReagendamentoNaoEncontradoException.class);

        verificarNadaMudou();
    }

    @ParameterizedTest
    @EnumSource(DecisaoReagendamento.class)
    void dadaPropostaInexistente_quandoTentaResponder_entao404SemTravarNada(DecisaoReagendamento decisao) {
        when(reagendamentoService.buscarAnuncioId(REAGENDAMENTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> responder(PRESTADOR_ID, decisao)).isInstanceOf(ReagendamentoNaoEncontradoException.class);

        verifyNoInteractions(trava);
        verificarNadaMudou();
    }

    // ---------------------------------------------------------------- estado e prazo (ambas as decisões)

    @ParameterizedTest
    @EnumSource(DecisaoReagendamento.class)
    void dadaPropostaPendenteQuePassouDoPrazo_quandoAContraparteResponde_entao409SemEfeito(
            DecisaoReagendamento decisao) {
        ReflectionTestUtils.setField(proposta, "expiraEm", AGORA);

        assertThatThrownBy(() -> responder(PRESTADOR_ID, decisao)).isInstanceOf(PropostaExpiradaException.class);

        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
        verificarNadaMudou();
    }

    @ParameterizedTest
    @EnumSource(value = StatusReagendamento.class, names = "PENDENTE", mode = EnumSource.Mode.EXCLUDE)
    void dadaPropostaJaEncerrada_quandoAContraparteResponde_entao409SemEfeito(StatusReagendamento status) {
        ReflectionTestUtils.setField(proposta, "status", status);

        assertThatThrownBy(() -> responder(PRESTADOR_ID, DecisaoReagendamento.ACEITAR))
                .isInstanceOf(PropostaEncerradaException.class);
        assertThatThrownBy(() -> responder(PRESTADOR_ID, DecisaoReagendamento.RECUSAR))
                .isInstanceOf(PropostaEncerradaException.class);

        assertThat(proposta.getStatus()).isEqualTo(status);
        verificarNadaMudou();
    }
}
