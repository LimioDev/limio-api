package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.eventos.ReagendamentoExpiradoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.ExpirarReagendamentosUseCase;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/** Job de expiração das propostas de reagendamento (ETI-99): o que expira, o que não expira e o que fica registrado. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpirarReagendamentosUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T12:00:00Z");
    private static final Instant DATA_VIGENTE = AGORA.plus(Duration.ofDays(3));
    private static final Instant DATA_PROPOSTA = AGORA.plus(Duration.ofDays(5));
    private static final int LOTE = 50;
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID REAGENDAMENTO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();

    @Mock
    private ReagendamentoService reagendamentoService;

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private LogAuditoriaService logAuditoriaService;

    @Mock
    private ApplicationEventPublisher eventos;

    @Mock
    private TravaTransacional trava;

    @Mock
    private PlatformTransactionManager gerenteTransacao;

    private ExpirarReagendamentosUseCase job;
    private Anuncio anuncio;

    @BeforeEach
    void montarJob() {
        job = new ExpirarReagendamentosUseCase(reagendamentoService, anuncioService, aplicacaoService,
                logAuditoriaService, eventos, trava, new TransactionTemplate(gerenteTransacao),
                Clock.fixed(AGORA, ZoneOffset.UTC), LOTE);
        anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .dataHoraPretendida(DATA_VIGENTE)
                .status(StatusAnuncio.PUBLICADO)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.buscarPrestadorDoServico(ANUNCIO_ID)).thenReturn(Optional.of(PRESTADOR_ID));
        when(reagendamentoService.buscarAnuncioId(REAGENDAMENTO_ID)).thenReturn(Optional.of(ANUNCIO_ID));
        when(reagendamentoService.salvar(any(Reagendamento.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---------------------------------------------------------------- cenário

    /** Candidata do job: proposta {@code PENDENTE} cujo prazo termina em {@code expiraEm}. */
    private Reagendamento candidata(Instant expiraEm) {
        Reagendamento proposta = Reagendamento.propor(ANUNCIO_ID, CONTRATANTE_ID, DATA_VIGENTE, DATA_PROPOSTA,
                "Imprevisto", expiraEm, false);
        ReflectionTestUtils.setField(proposta, "id", REAGENDAMENTO_ID);
        when(reagendamentoService.buscarIdsParaExpirar(AGORA, LOTE)).thenReturn(List.of(REAGENDAMENTO_ID));
        when(reagendamentoService.buscarPorId(REAGENDAMENTO_ID)).thenReturn(Optional.of(proposta));
        return proposta;
    }

    private void verificarNadaMudou() {
        verify(reagendamentoService, never()).salvar(any());
        verifyNoInteractions(logAuditoriaService, eventos);
    }

    // ---------------------------------------------------------------- expira

    @Test
    void dadaPropostaPendenteVencida_quandoExecuta_entaoExpiraSalvaAuditaEPublicaUmaVezEmTransacao() {
        Reagendamento proposta = candidata(AGORA.minusSeconds(60));

        job.executar();

        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.EXPIRADO);
        assertThat(proposta.getRespondidoEm()).isEqualTo(AGORA);
        InOrder ordem = inOrder(gerenteTransacao, trava, reagendamentoService, logAuditoriaService, eventos);
        ordem.verify(gerenteTransacao).getTransaction(any());
        ordem.verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());
        ordem.verify(reagendamentoService).buscarPorId(REAGENDAMENTO_ID);
        ordem.verify(reagendamentoService).salvar(proposta);
        ordem.verify(logAuditoriaService).registrar(eq("Reagendamento"), eq(REAGENDAMENTO_ID),
                eq(TipoAcaoAuditoria.REAGENDAMENTO_EXPIRADO), anyString(), isNull(), any(Map.class));
        ordem.verify(eventos).publishEvent(any(ReagendamentoExpiradoEvent.class));
        ordem.verify(gerenteTransacao).commit(any());
    }

    @Test
    void dadaPropostaVencida_quandoExpira_entaoADataOriginalContinuaVigenteEOAnuncioNaoMuda() {
        candidata(AGORA.minusSeconds(60));

        job.executar();

        assertThat(anuncio.getDataHoraPretendida()).isEqualTo(DATA_VIGENTE);
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        verify(anuncioService, never()).salvar(any());
    }

    @Test
    void dadaPropostaVencida_quandoExpira_entaoAuditaSemIpEPublicaEventoParaAsDuasPartes() {
        candidata(AGORA.minusSeconds(60));

        job.executar();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> detalhes = ArgumentCaptor.forClass(Map.class);
        verify(logAuditoriaService).registrar(eq("Reagendamento"), eq(REAGENDAMENTO_ID),
                eq(TipoAcaoAuditoria.REAGENDAMENTO_EXPIRADO), anyString(), isNull(), detalhes.capture());
        assertThat(detalhes.getValue())
                .containsEntry("anuncioId", ANUNCIO_ID.toString())
                .containsEntry("proponenteId", CONTRATANTE_ID.toString())
                .containsEntry("dataVigente", DATA_VIGENTE.toString())
                .containsEntry("dataProposta", DATA_PROPOSTA.toString());

        ArgumentCaptor<ReagendamentoExpiradoEvent> evento = ArgumentCaptor.forClass(ReagendamentoExpiradoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isEqualTo(new ReagendamentoExpiradoEvent(REAGENDAMENTO_ID, ANUNCIO_ID,
                "Instalar 3 tomadas", CONTRATANTE_ID, PRESTADOR_ID, CONTRATANTE_ID, DATA_VIGENTE, DATA_PROPOSTA,
                AGORA));
    }

    @Test
    void dadaPropostaQueVenceExatamenteAgora_quandoExecuta_entaoExpira() {
        Reagendamento proposta = candidata(AGORA);

        job.executar();

        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.EXPIRADO);
    }

    // ---------------------------------------------------------------- não expira

    @Test
    void dadaPropostaAindaDentroDoPrazo_quandoExecuta_entaoNaoExpira() {
        Reagendamento proposta = candidata(AGORA.plusMillis(1));

        job.executar();

        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
        verificarNadaMudou();
    }

    /** A pré-seleção devolveu o id, mas outra transação respondeu antes da trava: sob a trava ela já não está pendente. */
    @ParameterizedTest
    @EnumSource(value = StatusReagendamento.class, names = "PENDENTE", mode = EnumSource.Mode.EXCLUDE)
    void dadaPropostaJaRespondidaOuEncerradaAntesDaTrava_quandoExecuta_entaoNaoExpiraNemAuditaNemPublica(
            StatusReagendamento status) {
        Reagendamento proposta = candidata(AGORA.minusSeconds(60));
        ReflectionTestUtils.setField(proposta, "status", status);

        job.executar();

        assertThat(proposta.getStatus()).isEqualTo(status);
        verificarNadaMudou();
    }

    @Test
    void dadaPropostaQueSumiuEntreAPreSelecaoEATrava_quandoExecuta_entaoSaiSemEfeito() {
        candidata(AGORA.minusSeconds(60));
        when(reagendamentoService.buscarAnuncioId(REAGENDAMENTO_ID)).thenReturn(Optional.empty());

        job.executar();

        verifyNoInteractions(trava);
        verificarNadaMudou();
    }

    @Test
    void semNenhumaCandidata_quandoExecuta_entaoNaoAbreTransacao() {
        when(reagendamentoService.buscarIdsParaExpirar(AGORA, LOTE)).thenReturn(List.of());

        job.executar();

        verifyNoInteractions(gerenteTransacao, trava, logAuditoriaService, eventos);
    }

    // ---------------------------------------------------------------- falhas

    @Test
    void dadaFalhaNumaProposta_quandoExecuta_entaoAsDemaisContinuamEATransacaoDaFalhaDesfaz() {
        UUID outraId = UUID.randomUUID();
        Reagendamento primeira = candidata(AGORA.minusSeconds(60));
        Reagendamento segunda = Reagendamento.propor(ANUNCIO_ID, CONTRATANTE_ID, DATA_VIGENTE, DATA_PROPOSTA,
                "Outra", AGORA.minusSeconds(30), false);
        ReflectionTestUtils.setField(segunda, "id", outraId);
        when(reagendamentoService.buscarIdsParaExpirar(AGORA, LOTE)).thenReturn(List.of(REAGENDAMENTO_ID, outraId));
        when(reagendamentoService.buscarAnuncioId(outraId)).thenReturn(Optional.of(ANUNCIO_ID));
        when(reagendamentoService.buscarPorId(outraId)).thenReturn(Optional.of(segunda));
        doThrow(new IllegalStateException("falha simulada")).when(reagendamentoService).salvar(primeira);

        job.executar(); // a falha é logada, não propaga

        assertThat(segunda.getStatus()).isEqualTo(StatusReagendamento.EXPIRADO);
        verify(gerenteTransacao).rollback(any());
        verify(gerenteTransacao).commit(any());
        ArgumentCaptor<ReagendamentoExpiradoEvent> evento = ArgumentCaptor.forClass(ReagendamentoExpiradoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().reagendamentoId()).isEqualTo(outraId);
    }
}
