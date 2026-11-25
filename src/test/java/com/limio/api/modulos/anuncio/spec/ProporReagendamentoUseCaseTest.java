package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.limio.api.comum.eventos.ReagendamentoPropostoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.ProporReagendamentoUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.excecao.DataIgualAVigenteException;
import com.limio.api.modulos.anuncio.excecao.DataNoPassadoException;
import com.limio.api.modulos.anuncio.excecao.LimiteReagendamentosException;
import com.limio.api.modulos.anuncio.excecao.PropostaPendenteExistenteException;
import com.limio.api.modulos.anuncio.excecao.ReagendamentoNaoPermitidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/** Propor reagendamento (ETI-99, US46): as validações, a ordem delas e os efeitos, com tudo em volta mockado. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProporReagendamentoUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID ESTRANHO_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-11T15:00:00Z");
    private static final Instant DATA_VIGENTE = AGORA.plus(Duration.ofDays(3));
    private static final Instant NOVA_DATA = AGORA.plus(Duration.ofDays(5));
    private static final String JUSTIFICATIVA = "Imprevisto de agenda";
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

    private ProporReagendamentoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ProporReagendamentoUseCase(anuncioService, aplicacaoService, reagendamentoService,
                logAuditoriaService, eventos, trava, Clock.fixed(AGORA, ZoneOffset.UTC), 2, 24);
        when(reagendamentoService.salvar(any(Reagendamento.class))).thenAnswer(invocacao -> {
            Reagendamento salvo = invocacao.getArgument(0);
            ReflectionTestUtils.setField(salvo, "id", UUID.randomUUID());
            return salvo;
        });
    }

    // ---------------------------------------------------------------- cenário

    private Anuncio anuncio(StatusAnuncio status, Instant dataVigente) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .dataHoraPretendida(dataVigente)
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    private Aplicacao escolhida() {
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

    /** Serviço combinado: anúncio {@code PUBLICADO} com a candidatura do Prestador {@code ESCOLHIDA}. */
    private Anuncio combinado(Instant dataVigente) {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, dataVigente);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.buscarPrestadorEscolhido(ANUNCIO_ID)).thenReturn(Optional.of(PRESTADOR_ID));
        when(aplicacaoService.buscarEscolhidaDoAnuncioParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));
        return anuncio;
    }

    private Reagendamento propor(UUID usuarioId, Instant novaData) {
        return useCase.executar(usuarioId, ANUNCIO_ID, novaData, JUSTIFICATIVA, IP).reagendamento();
    }

    private void verificarNadaMudou() {
        verify(reagendamentoService, never()).salvar(any());
        verifyNoInteractions(logAuditoriaService, eventos);
    }

    // ---------------------------------------------------------------- caminho feliz

    @Test
    void dadoEmpregador_quandoPropoe_entaoGravaPendenteComPrazoDe24hEMantemOAnuncio() {
        Anuncio anuncio = combinado(DATA_VIGENTE);

        var consultado = useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, NOVA_DATA, JUSTIFICATIVA, IP);
        Reagendamento proposta = consultado.reagendamento();

        assertThat(consultado.dataVigente()).isEqualTo(DATA_VIGENTE);
        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
        assertThat(proposta.getAnuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(proposta.getProponenteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(proposta.getDataAnterior()).isEqualTo(DATA_VIGENTE);
        assertThat(proposta.getDataProposta()).isEqualTo(NOVA_DATA);
        assertThat(proposta.getJustificativa()).isEqualTo(JUSTIFICATIVA);
        assertThat(proposta.getExpiraEm()).isEqualTo(AGORA.plus(Duration.ofHours(24)));
        assertThat(proposta.isPropostaAposDataVigente()).isFalse();
        assertThat(proposta.getRespondidoEm()).isNull();
        assertThat(anuncio.getDataHoraPretendida()).isEqualTo(DATA_VIGENTE);
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        verify(anuncioService, never()).salvar(any());
    }

    @Test
    void dadoPrestadorEscolhido_quandoPropoe_entaoOProponenteEOPrestador() {
        combinado(DATA_VIGENTE);

        Reagendamento proposta = propor(PRESTADOR_ID, NOVA_DATA);

        assertThat(proposta.getProponenteId()).isEqualTo(PRESTADOR_ID);
        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
    }

    @Test
    void dadaJustificativaComEspacosNasPontas_quandoPropoe_entaoGravaSemOsEspacos() {
        combinado(DATA_VIGENTE);

        Reagendamento proposta = useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, NOVA_DATA, "  Imprevisto  ", IP)
                .reagendamento();

        assertThat(proposta.getJustificativa()).isEqualTo("Imprevisto");
    }

    @Test
    void dadoServicoCombinado_quandoPropoe_entaoTravaOAnuncioAntesDeLerOEstado() {
        combinado(DATA_VIGENTE);

        propor(CONTRATANTE_ID, NOVA_DATA);

        InOrder ordem = inOrder(trava, anuncioService, reagendamentoService);
        ordem.verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());
        ordem.verify(anuncioService).buscarPorId(ANUNCIO_ID);
        ordem.verify(reagendamentoService).salvar(any(Reagendamento.class));
    }

    @Test
    void dadaProposta_quandoPropoe_entaoAuditaComAsDatasEPublicaOEvento() {
        combinado(DATA_VIGENTE);

        Reagendamento proposta = propor(CONTRATANTE_ID, NOVA_DATA);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> detalhes = ArgumentCaptor.forClass(Map.class);
        verify(logAuditoriaService).registrar(
                org.mockito.ArgumentMatchers.eq("Reagendamento"), org.mockito.ArgumentMatchers.eq(proposta.getId()),
                org.mockito.ArgumentMatchers.eq(TipoAcaoAuditoria.REAGENDAMENTO_PROPOSTO),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(IP), detalhes.capture());
        assertThat(detalhes.getValue())
                .containsEntry("anuncioId", ANUNCIO_ID.toString())
                .containsEntry("proponenteId", CONTRATANTE_ID.toString())
                .containsEntry("dataAnterior", DATA_VIGENTE.toString())
                .containsEntry("dataProposta", NOVA_DATA.toString());

        ArgumentCaptor<ReagendamentoPropostoEvent> evento = ArgumentCaptor.forClass(ReagendamentoPropostoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().reagendamentoId()).isEqualTo(proposta.getId());
        assertThat(evento.getValue().contratanteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().prestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(evento.getValue().proponenteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().dataAnterior()).isEqualTo(DATA_VIGENTE);
        assertThat(evento.getValue().dataProposta()).isEqualTo(NOVA_DATA);
        assertThat(evento.getValue().expiraEm()).isEqualTo(AGORA.plus(Duration.ofHours(24)));
        assertThat(evento.getValue().ocorridoEm()).isEqualTo(AGORA);
    }

    // ---------------------------------------------------------------- autorização e estado

    @Test
    void dadoAnuncioInexistente_quandoPropoe_entao404SemEfeito() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> propor(CONTRATANTE_ID, NOVA_DATA)).isInstanceOf(ServicoNaoEncontradoException.class);

        verifyNoInteractions(reagendamentoService, logAuditoriaService, eventos);
    }

    @Test
    void dadoUsuarioQueNaoEParte_quandoPropoe_entao404SemEfeito() {
        combinado(DATA_VIGENTE);

        assertThatThrownBy(() -> propor(ESTRANHO_ID, NOVA_DATA)).isInstanceOf(ServicoNaoEncontradoException.class);

        verifyNoInteractions(reagendamentoService, logAuditoriaService, eventos);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = "PUBLICADO", mode = EnumSource.Mode.EXCLUDE)
    void dadoServicoForaDeCombinado_quandoPropoe_entao409SemEfeito(StatusAnuncio status) {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(status, DATA_VIGENTE)));
        when(aplicacaoService.buscarPrestadorEscolhido(ANUNCIO_ID)).thenReturn(Optional.of(PRESTADOR_ID));

        assertThatThrownBy(() -> propor(PRESTADOR_ID, NOVA_DATA)).isInstanceOf(ServicoEstadoInvalidoException.class);

        verifyNoInteractions(reagendamentoService, logAuditoriaService, eventos);
    }

    @Test
    void dadoAnuncioPublicadoSemCandidaturaEscolhida_quandoEmpregadorPropoe_entao409SemEfeito() {
        when(anuncioService.buscarPorId(ANUNCIO_ID))
                .thenReturn(Optional.of(anuncio(StatusAnuncio.PUBLICADO, DATA_VIGENTE)));
        when(aplicacaoService.buscarEscolhidaDoAnuncioParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> propor(CONTRATANTE_ID, NOVA_DATA)).isInstanceOf(ServicoEstadoInvalidoException.class);

        verifyNoInteractions(reagendamentoService, logAuditoriaService, eventos);
    }

    // ---------------------------------------------------------------- data proposta

    @Test
    void dadaNovaDataNoPassado_quandoPropoe_entao422SemEfeito() {
        combinado(DATA_VIGENTE);

        assertThatThrownBy(() -> propor(CONTRATANTE_ID, AGORA.minusSeconds(1))).isInstanceOf(DataNoPassadoException.class);

        verificarNadaMudou();
    }

    @Test
    void dadaNovaDataIgualAAgora_quandoPropoe_entao422SemEfeito() {
        combinado(DATA_VIGENTE);

        assertThatThrownBy(() -> propor(CONTRATANTE_ID, AGORA)).isInstanceOf(DataNoPassadoException.class);

        verificarNadaMudou();
    }

    @Test
    void dadaNovaDataAntesDoPrazoDeResposta_quandoPropoe_entaoExpiraNaNovaData() {
        combinado(DATA_VIGENTE);
        Instant novaData = AGORA.plus(Duration.ofHours(2));

        Reagendamento proposta = propor(CONTRATANTE_ID, novaData);

        assertThat(proposta.getExpiraEm()).isEqualTo(novaData);
    }

    @Test
    void dadaNovaDataIgualAoFimDoPrazo_quandoPropoe_entaoExpiraNoPrazo() {
        combinado(DATA_VIGENTE);

        Reagendamento proposta = propor(CONTRATANTE_ID, AGORA.plus(Duration.ofHours(24)));

        assertThat(proposta.getExpiraEm()).isEqualTo(AGORA.plus(Duration.ofHours(24)));
    }

    @Test
    void dadaNovaDataIgualAVigente_quandoPropoe_entao422SemEfeito() {
        combinado(DATA_VIGENTE);

        assertThatThrownBy(() -> propor(CONTRATANTE_ID, DATA_VIGENTE)).isInstanceOf(DataIgualAVigenteException.class);

        verificarNadaMudou();
    }

    // ---------------------------------------------------------------- pendente e limite

    @Test
    void dadaPropostaPendente_quandoPropoe_entao409SemEfeito() {
        combinado(DATA_VIGENTE);
        when(reagendamentoService.existePendente(ANUNCIO_ID)).thenReturn(true);

        assertThatThrownBy(() -> propor(PRESTADOR_ID, NOVA_DATA)).isInstanceOf(PropostaPendenteExistenteException.class);

        verificarNadaMudou();
    }

    @Test
    void dadoLimiteDeAceitosAtingido_quandoPropoe_entao409SemEfeito() {
        combinado(DATA_VIGENTE);
        when(reagendamentoService.contarAceitos(ANUNCIO_ID)).thenReturn(2);

        assertThatThrownBy(() -> propor(CONTRATANTE_ID, NOVA_DATA)).isInstanceOf(LimiteReagendamentosException.class);

        verificarNadaMudou();
    }

    @Test
    void dadoUmAceitoAbaixoDoLimite_quandoPropoe_entaoPermite() {
        combinado(DATA_VIGENTE);
        when(reagendamentoService.contarAceitos(ANUNCIO_ID)).thenReturn(1);

        Reagendamento proposta = propor(CONTRATANTE_ID, NOVA_DATA);

        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
    }

    // ---------------------------------------------------------------- depois da data vigente

    @Test
    void dadaDataVigenteJaPassada_quandoPropoeAPrimeira_entaoPermiteEMarcaAProposta() {
        combinado(AGORA.minus(Duration.ofHours(2)));

        Reagendamento proposta = propor(CONTRATANTE_ID, NOVA_DATA);

        assertThat(proposta.isPropostaAposDataVigente()).isTrue();
        assertThat(proposta.getDataAnterior()).isEqualTo(AGORA.minus(Duration.ofHours(2)));
    }

    @Test
    void dadaDataVigenteExatamenteAgora_quandoPropoe_entaoContaComoDepoisDaDataVigente() {
        combinado(AGORA);

        Reagendamento proposta = propor(CONTRATANTE_ID, NOVA_DATA);

        assertThat(proposta.isPropostaAposDataVigente()).isTrue();
    }

    @Test
    void dadaDataVigenteJaPassadaEPropostaAnteriorNessaSituacao_quandoPropoeDeNovo_entao409SemEfeito() {
        combinado(AGORA.minus(Duration.ofHours(2)));
        when(reagendamentoService.existePropostaAposDataVigente(ANUNCIO_ID)).thenReturn(true);

        assertThatThrownBy(() -> propor(PRESTADOR_ID, NOVA_DATA)).isInstanceOf(ReagendamentoNaoPermitidoException.class);

        verificarNadaMudou();
    }

    @Test
    void dadaDataVigenteAindaNoFuturo_quandoJaHouvePropostaAposDataVigente_entaoPermite() {
        combinado(DATA_VIGENTE);
        when(reagendamentoService.existePropostaAposDataVigente(ANUNCIO_ID)).thenReturn(true);

        Reagendamento proposta = propor(CONTRATANTE_ID, NOVA_DATA);

        assertThat(proposta.isPropostaAposDataVigente()).isFalse();
    }
}
