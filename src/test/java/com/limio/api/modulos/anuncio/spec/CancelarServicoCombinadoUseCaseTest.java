package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
import com.limio.api.comum.eventos.ServicoCanceladoEvent;
import com.limio.api.comum.eventos.enums.TipoCancelamento;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.portas.ConsumoCotaPrestador;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.CancelamentoServico;
import com.limio.api.modulos.anuncio.actions.usecase.CancelarServicoCombinadoUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.excecao.PrazoCancelamentoEsgotadoException;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.UseDenunciaException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CancelarServicoCombinadoUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-11T15:00:00Z");
    private static final Instant ESCOLHIDA_EM = AGORA.minus(Duration.ofDays(2));
    private static final String IP = "203.0.113.7";

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ReagendamentoService reagendamentoService;

    @Mock
    private ConsumoCotaPrestador consumoCotaPrestador;

    @Mock
    private LogAuditoriaService logAuditoriaService;

    @Mock
    private ApplicationEventPublisher eventos;

    @Mock
    private TravaTransacional trava;

    private CancelarServicoCombinadoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new CancelarServicoCombinadoUseCase(anuncioService, aplicacaoService, reagendamentoService,
                consumoCotaPrestador, logAuditoriaService, eventos, trava, Clock.fixed(AGORA, ZoneOffset.UTC),
                JsonMapper.builder().build(), 24);
        lenient().when(anuncioService.salvar(any(Anuncio.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(reagendamentoService.buscarUltimoAceito(ANUNCIO_ID)).thenReturn(Optional.empty());
        lenient().when(reagendamentoService.buscarPendentesDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of());
    }

    // ---------------------------------------------------------------- cenário

    private Anuncio anuncio(StatusAnuncio status, Instant dataHora) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .dataHoraPretendida(dataHora)
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    private Aplicacao escolhida(boolean cotaConsumida) {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(ANUNCIO_ID)
                .prestadorId(PRESTADOR_ID)
                .status(StatusAplicacao.ESCOLHIDA)
                .dataAplicacao(ESCOLHIDA_EM.minus(Duration.ofHours(1)))
                .escolhidaEm(ESCOLHIDA_EM)
                .cotaConsumida(cotaConsumida)
                .build();
        ReflectionTestUtils.setField(aplicacao, "id", UUID.randomUUID());
        return aplicacao;
    }

    private Reagendamento reagendamento(StatusReagendamento status, Instant respondidoEm) {
        return Reagendamento.builder()
                .anuncioId(ANUNCIO_ID)
                .dataAnterior(Instant.parse("2026-11-20T15:00:00Z"))
                .dataProposta(Instant.parse("2026-11-25T15:00:00Z"))
                .status(status)
                .respondidoEm(respondidoEm)
                .build();
    }

    /** Anúncio {@code PUBLICADO} com a candidatura escolhida; a data vigente é {@code AGORA + antecedencia}. */
    private Anuncio combinado(Duration antecedencia, Aplicacao escolhida) {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, AGORA.plus(antecedencia));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.buscarEscolhidaDoAnuncioParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.of(escolhida));
        return anuncio;
    }

    private void aceitoHa(Duration ha) {
        when(reagendamentoService.buscarUltimoAceito(ANUNCIO_ID))
                .thenReturn(Optional.of(reagendamento(StatusReagendamento.ACEITO, AGORA.minus(ha))));
    }

    private ServicoCanceladoEvent eventoPublicado() {
        ArgumentCaptor<ServicoCanceladoEvent> evento = ArgumentCaptor.forClass(ServicoCanceladoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        return evento.getValue();
    }

    private void verificarNadaMudou(Anuncio anuncio, Aplicacao escolhida) {
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(anuncio.getTipoCancelamento()).isNull();
        assertThat(escolhida.getStatus()).isEqualTo(StatusAplicacao.ESCOLHIDA);
        verify(anuncioService, never()).salvar(any());
        verify(aplicacaoService, never()).salvar(any());
        verifyNoInteractions(consumoCotaPrestador, logAuditoriaService, eventos);
    }

    // ---------------------------------------------------------------- cancelamento normal

    @Test
    void dadoCombinadoComTresDiasEPrestadorFree_quandoCancela_entaoEncerraEstornaECotaEPublicaEvento() {
        Aplicacao escolhida = escolhida(true);
        Anuncio anuncio = combinado(Duration.ofDays(3), escolhida);
        when(consumoCotaPrestador.estornarEscolha(PRESTADOR_ID, ESCOLHIDA_EM)).thenReturn(true);

        CancelamentoServico resultado = useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP);

        assertThat(resultado.anuncio()).isSameAs(anuncio);
        assertThat(resultado.cotaEstornada()).isTrue();
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(anuncio.getTipoCancelamento()).isEqualTo(TipoCancelamento.NORMAL);
        assertThat(anuncio.getEncerradoEm()).isEqualTo(AGORA);
        assertThat(anuncio.getMotivoEncerramento()).isEqualTo("Serviço cancelado pelo Empregador");
        assertThat(escolhida.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(escolhida.getEncerradaEm()).isEqualTo(AGORA);
        assertThat(escolhida.getCotaEstornadaEm()).isEqualTo(AGORA);
        verify(aplicacaoService).salvar(escolhida);
        verifyNoInteractions(logAuditoriaService);

        ServicoCanceladoEvent evento = eventoPublicado();
        assertThat(evento.anuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(evento.anuncioTitulo()).isEqualTo("Instalar 3 tomadas");
        assertThat(evento.candidaturaEscolhidaId()).isEqualTo(escolhida.getId());
        assertThat(evento.contratanteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.prestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(evento.tipo()).isEqualTo(TipoCancelamento.NORMAL);
        assertThat(evento.cotaEstornada()).isTrue();
        assertThat(evento.dataHoraVigente()).isEqualTo(AGORA.plus(Duration.ofDays(3)));
        assertThat(evento.ocorridoEm()).isEqualTo(AGORA);
    }

    @Test
    void dadoCancelamento_quandoExecuta_entaoTravaAnuncioDepoisLinhaDepoisCotaEGrava() {
        Aplicacao escolhida = escolhida(true);
        combinado(Duration.ofDays(3), escolhida);
        when(consumoCotaPrestador.estornarEscolha(any(), any())).thenReturn(true);

        useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP);

        InOrder ordem = inOrder(trava, anuncioService, aplicacaoService, consumoCotaPrestador, eventos);
        ordem.verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());
        ordem.verify(anuncioService).buscarPorId(ANUNCIO_ID);
        ordem.verify(aplicacaoService).buscarEscolhidaDoAnuncioParaAtualizar(ANUNCIO_ID);
        ordem.verify(consumoCotaPrestador).estornarEscolha(PRESTADOR_ID, ESCOLHIDA_EM);
        ordem.verify(aplicacaoService).salvar(escolhida);
        ordem.verify(eventos).publishEvent(any(ServicoCanceladoEvent.class));
    }

    @Test
    void dadoPrestadorGoldNaEscolha_quandoCancela_entaoNaoEstornaNada() {
        Aplicacao escolhida = escolhida(false);
        Anuncio anuncio = combinado(Duration.ofDays(3), escolhida);

        CancelamentoServico resultado = useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP);

        assertThat(resultado.cotaEstornada()).isFalse();
        assertThat(anuncio.getTipoCancelamento()).isEqualTo(TipoCancelamento.NORMAL);
        assertThat(escolhida.getCotaEstornadaEm()).isNull();
        verifyNoInteractions(consumoCotaPrestador);
        assertThat(eventoPublicado().cotaEstornada()).isFalse();
    }

    @Test
    void dadoNadaADevolverNaCota_quandoCancela_entaoCancelaSemMarcarEstorno() {
        Aplicacao escolhida = escolhida(true);
        Anuncio anuncio = combinado(Duration.ofDays(3), escolhida);
        when(consumoCotaPrestador.estornarEscolha(PRESTADOR_ID, ESCOLHIDA_EM)).thenReturn(false);

        CancelamentoServico resultado = useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP);

        assertThat(resultado.cotaEstornada()).isFalse();
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(escolhida.getCotaEstornadaEm()).isNull();
        assertThat(eventoPublicado().cotaEstornada()).isFalse();
    }

    // ---------------------------------------------------------------- antecedência de 24h

    @Test
    void dadoMenosDe24Horas_quandoCancela_entaoRecusaSemMudarNemEstornarNemPublicar() {
        Aplicacao escolhida = escolhida(true);
        Anuncio anuncio = combinado(Duration.ofHours(24).minusSeconds(1), escolhida);

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP))
                .isInstanceOf(PrazoCancelamentoEsgotadoException.class);

        verificarNadaMudou(anuncio, escolhida);
    }

    @Test
    void dadoExatamente24Horas_quandoCancela_entaoPermite() {
        Aplicacao escolhida = escolhida(true);
        Anuncio anuncio = combinado(Duration.ofHours(24), escolhida);
        when(consumoCotaPrestador.estornarEscolha(any(), any())).thenReturn(true);

        useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
    }

    @Test
    void dadaDataJaPassada_quandoCancela_entaoRecusa() {
        Aplicacao escolhida = escolhida(true);
        Anuncio anuncio = combinado(Duration.ofHours(-1), escolhida);

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP))
                .isInstanceOf(PrazoCancelamentoEsgotadoException.class);

        verificarNadaMudou(anuncio, escolhida);
    }

    // ---------------------------------------------------------------- carência pós-reagendamento

    @Test
    void dadoReagendamentoAceitoDentroDaCarencia_quandoCancela_entaoTardioSemEstornoComAuditoria() {
        Aplicacao escolhida = escolhida(true);
        Anuncio anuncio = combinado(Duration.ofDays(5), escolhida);
        aceitoHa(Duration.ofHours(2));

        CancelamentoServico resultado = useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP);

        assertThat(resultado.cotaEstornada()).isFalse();
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(anuncio.getTipoCancelamento()).isEqualTo(TipoCancelamento.TARDIO);
        assertThat(anuncio.getMotivoEncerramento())
                .isEqualTo("Serviço cancelado pelo Empregador na carência pós-reagendamento");
        assertThat(escolhida.getCotaEstornadaEm()).isNull();
        verifyNoInteractions(consumoCotaPrestador);

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(logAuditoriaService).registrar(eq("Anuncio"), eq(ANUNCIO_ID),
                eq(TipoAcaoAuditoria.COMBINADO_CANCELADO_TARDIO), eq("anuncio-cancelar-servico"), eq(IP),
                detalhes.capture());
        assertThat(detalhes.getValue()).contains("\"dataAnterior\":\"2026-11-20T15:00:00Z\"")
                .contains("\"dataNova\":\"2026-11-25T15:00:00Z\"")
                .contains("\"aceitoEm\":\"" + AGORA.minus(Duration.ofHours(2)) + "\"");

        ServicoCanceladoEvent evento = eventoPublicado();
        assertThat(evento.tipo()).isEqualTo(TipoCancelamento.TARDIO);
        assertThat(evento.cotaEstornada()).isFalse();
    }

    @Test
    void dadoCarenciaQueAcabaDeTerminar_quandoCancela_entaoNormal() {
        Aplicacao escolhida = escolhida(true);
        Anuncio anuncio = combinado(Duration.ofDays(5), escolhida);
        aceitoHa(Duration.ofHours(24));
        when(consumoCotaPrestador.estornarEscolha(any(), any())).thenReturn(true);

        useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP);

        assertThat(anuncio.getTipoCancelamento()).isEqualTo(TipoCancelamento.NORMAL);
        verifyNoInteractions(logAuditoriaService);
    }

    @Test
    void dadoCarenciaPrestesATerminar_quandoCancela_entaoAindaTardio() {
        Aplicacao escolhida = escolhida(true);
        Anuncio anuncio = combinado(Duration.ofDays(5), escolhida);
        aceitoHa(Duration.ofHours(24).minusSeconds(1));

        useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP);

        assertThat(anuncio.getTipoCancelamento()).isEqualTo(TipoCancelamento.TARDIO);
    }

    @Test
    void dadoReagendamentoAceitoForaDaCarencia_quandoCancela_entaoNormalComEstorno() {
        Aplicacao escolhida = escolhida(true);
        Anuncio anuncio = combinado(Duration.ofDays(5), escolhida);
        aceitoHa(Duration.ofHours(25));
        when(consumoCotaPrestador.estornarEscolha(PRESTADOR_ID, ESCOLHIDA_EM)).thenReturn(true);

        CancelamentoServico resultado = useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP);

        assertThat(resultado.cotaEstornada()).isTrue();
        assertThat(anuncio.getTipoCancelamento()).isEqualTo(TipoCancelamento.NORMAL);
    }

    @Test
    void dadoCarenciaMasMenosDe24HorasAteADataVigente_quandoCancela_entaoRecusa() {
        Aplicacao escolhida = escolhida(true);
        Anuncio anuncio = combinado(Duration.ofHours(10), escolhida);
        aceitoHa(Duration.ofHours(1));

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP))
                .isInstanceOf(PrazoCancelamentoEsgotadoException.class);

        verificarNadaMudou(anuncio, escolhida);
    }

    // ---------------------------------------------------------------- proposta pendente

    @Test
    void dadaPropostaDeReagendamentoPendente_quandoCancela_entaoPerdeOEfeito() {
        Aplicacao escolhida = escolhida(true);
        combinado(Duration.ofDays(3), escolhida);
        Reagendamento pendente = reagendamento(StatusReagendamento.PENDENTE, null);
        when(reagendamentoService.buscarPendentesDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of(pendente));
        when(consumoCotaPrestador.estornarEscolha(any(), any())).thenReturn(true);

        useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP);

        assertThat(pendente.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
        assertThat(pendente.getRespondidoEm()).isEqualTo(AGORA);
        verify(reagendamentoService).salvar(pendente);
    }

    // ---------------------------------------------------------------- estado e autorização

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = {"EM_ANDAMENTO", "AGUARDANDO_VALIDACAO"})
    void dadoServicoEmAndamentoOuAguardandoValidacao_quandoCancela_entaoOrientaDenuncia(StatusAnuncio status) {
        Anuncio anuncio = anuncio(status, AGORA.plus(Duration.ofDays(3)));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP))
                .isInstanceOf(UseDenunciaException.class);

        assertThat(anuncio.getStatus()).isEqualTo(status);
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(aplicacaoService, consumoCotaPrestador, logAuditoriaService, eventos);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = {"CONCLUIDO", "ENCERRADO"})
    void dadoServicoTerminal_quandoCancela_entaoRecusaSemMudarNemEstornar(StatusAnuncio status) {
        Anuncio anuncio = anuncio(status, AGORA.plus(Duration.ofDays(3)));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP))
                .isInstanceOf(ServicoEstadoInvalidoException.class);

        assertThat(anuncio.getStatus()).isEqualTo(status);
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(aplicacaoService, consumoCotaPrestador, logAuditoriaService, eventos);
    }

    @Test
    void dadoPublicadoSemCandidaturaEscolhida_quandoCancela_entaoEstadoInvalido() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, AGORA.plus(Duration.ofDays(3)));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.buscarEscolhidaDoAnuncioParaAtualizar(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP))
                .isInstanceOf(ServicoEstadoInvalidoException.class);

        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(consumoCotaPrestador, logAuditoriaService, eventos);
    }

    @Test
    void dadoQuemNaoEODono_quandoCancela_entaoNaoEncontradoSemTocarEmNada() {
        Aplicacao escolhida = escolhida(true);
        Anuncio anuncio = combinado(Duration.ofDays(3), escolhida);

        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), ANUNCIO_ID, IP))
                .isInstanceOf(ServicoNaoEncontradoException.class);

        verificarNadaMudou(anuncio, escolhida);
        verify(aplicacaoService, never()).buscarEscolhidaDoAnuncioParaAtualizar(any());
    }

    @Test
    void dadoAnuncioInexistente_quandoCancela_entaoNaoEncontrado() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, IP))
                .isInstanceOf(ServicoNaoEncontradoException.class);

        verifyNoInteractions(consumoCotaPrestador, logAuditoriaService, eventos);
        verify(trava).travar(eq(EscopoTrava.ANUNCIO), anyString());
    }
}
