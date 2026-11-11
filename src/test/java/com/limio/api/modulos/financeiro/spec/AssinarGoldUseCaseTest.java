package com.limio.api.modulos.financeiro.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.financeiro.actions.service.GatewayPagamento;
import com.limio.api.modulos.financeiro.actions.service.ResultadoCobranca;
import com.limio.api.modulos.financeiro.actions.usecase.AssinarGoldUseCase;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.Cobranca;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.CobrancaService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal.DocumentoFiscal;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal.DocumentoFiscalService;
import com.limio.api.modulos.financeiro.assinaturagold.enums.StatusAssinatura;
import com.limio.api.modulos.financeiro.eventos.GoldAssinadoEvent;
import com.limio.api.modulos.financeiro.excecao.AssinaturaJaAtivaException;
import com.limio.api.modulos.financeiro.excecao.PagamentoRecusadoException;
import com.limio.api.modulos.financeiro.excecao.PlanoNaoEncontradoException;
import com.limio.api.modulos.financeiro.excecao.SomentePrestadorAssinaGoldException;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamentoService;
import com.limio.api.modulos.financeiro.plano.Plano;
import com.limio.api.modulos.financeiro.plano.PlanoService;
import com.limio.api.modulos.financeiro.plano.enums.CicloPlano;

@ExtendWith(MockitoExtension.class)
class AssinarGoldUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID PLANO_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-10T12:00:00Z");
    private static final String TOKEN = "tok_abc123";

    @Mock
    private PlanoService planoService;

    @Mock
    private AssinaturaGoldService assinaturaGoldService;

    @Mock
    private MeioPagamentoService meioPagamentoService;

    @Mock
    private CobrancaService cobrancaService;

    @Mock
    private DocumentoFiscalService documentoFiscalService;

    @Mock
    private GatewayPagamento gatewayPagamento;

    @Mock
    private TravaTransacional travaTransacional;

    @Mock
    private ApplicationEventPublisher eventos;

    private AssinarGoldUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new AssinarGoldUseCase(planoService, assinaturaGoldService, meioPagamentoService, cobrancaService,
                documentoFiscalService, gatewayPagamento, travaTransacional, eventos,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static Plano planoGold() {
        Plano plano = Plano.builder()
                .codigo(Plano.CODIGO_GOLD)
                .nome("Plano GOLD")
                .precoCentavos(2990)
                .moeda("BRL")
                .ciclo(CicloPlano.MENSAL)
                .ativo(true)
                .build();
        ReflectionTestUtils.setField(plano, "id", PLANO_ID);
        return plano;
    }

    /** Monta os mocks do caminho feliz; cada teste sobrescreve só o que precisa mudar. */
    private void prepararCaminhoFeliz() {
        when(planoService.buscarAtivoPorCodigo(Plano.CODIGO_GOLD)).thenReturn(Optional.of(planoGold()));
        when(assinaturaGoldService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());
        when(gatewayPagamento.cobrar(eq(USUARIO_ID), eq(TOKEN), eq(2990L), eq("BRL"), anyString()))
                .thenReturn(new ResultadoCobranca(true, "SIM-123", "VISA", "9876"));
        when(assinaturaGoldService.salvar(any())).thenAnswer(chamada -> {
            AssinaturaGold a = chamada.getArgument(0);
            if (a.getId() == null) {
                ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
            }
            return a;
        });
        when(cobrancaService.salvar(any())).thenAnswer(chamada -> {
            Cobranca c = chamada.getArgument(0);
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            return c;
        });
        when(documentoFiscalService.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void dadoPagamentoAprovado_quandoAssina_entaoAtivaEGravaTudo() {
        prepararCaminhoFeliz();

        AssinaturaGold resultado = useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR, Plano.CODIGO_GOLD, TOKEN);

        assertThat(resultado.estaAtiva()).isTrue();
        assertThat(resultado.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(resultado.getPlanoId()).isEqualTo(PLANO_ID);
        assertThat(resultado.getDataInicioCiclo()).isEqualTo(AGORA);
        assertThat(resultado.getDataFimCiclo()).isEqualTo(AGORA.atZone(ZoneOffset.UTC).plusMonths(1).toInstant());

        verify(travaTransacional).travar(EscopoTrava.ASSINATURA_GOLD, USUARIO_ID.toString());
        verify(meioPagamentoService).salvarOuSubstituir(USUARIO_ID, TOKEN, "VISA", "9876");

        ArgumentCaptor<Cobranca> cobrancaCaptor = ArgumentCaptor.forClass(Cobranca.class);
        verify(cobrancaService).salvar(cobrancaCaptor.capture());
        assertThat(cobrancaCaptor.getValue().getValorCentavos()).isEqualTo(2990L);
        assertThat(cobrancaCaptor.getValue().getGatewayCobrancaId()).isEqualTo("SIM-123");

        verify(documentoFiscalService).salvar(any());

        ArgumentCaptor<GoldAssinadoEvent> eventoCaptor = ArgumentCaptor.forClass(GoldAssinadoEvent.class);
        verify(eventos).publishEvent(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().usuarioId()).isEqualTo(USUARIO_ID);
        assertThat(eventoCaptor.getValue().valorCentavos()).isEqualTo(2990L);
    }

    @Test
    void dadoPagamentoRecusado_quandoAssina_entaoRecusaSemPersistirNada() {
        when(planoService.buscarAtivoPorCodigo(Plano.CODIGO_GOLD)).thenReturn(Optional.of(planoGold()));
        when(assinaturaGoldService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());
        when(gatewayPagamento.cobrar(eq(USUARIO_ID), eq(TOKEN), eq(2990L), eq("BRL"), anyString()))
                .thenReturn(ResultadoCobranca.recusada());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR, Plano.CODIGO_GOLD, TOKEN))
                .isInstanceOf(PagamentoRecusadoException.class);

        verify(meioPagamentoService, never()).salvarOuSubstituir(any(), any(), any(), any());
        verify(assinaturaGoldService, never()).salvar(any());
        verify(cobrancaService, never()).salvar(any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void dadoPrestadorJaGold_quandoAssinaDeNovo_entaoAssinaturaJaAtivaSemChamarGateway() {
        AssinaturaGold ativa = AssinaturaGold.nova(USUARIO_ID, PLANO_ID, AGORA.minusSeconds(3600),
                AGORA.plusSeconds(3600));
        when(planoService.buscarAtivoPorCodigo(Plano.CODIGO_GOLD)).thenReturn(Optional.of(planoGold()));
        when(assinaturaGoldService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(ativa));

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR, Plano.CODIGO_GOLD, TOKEN))
                .isInstanceOf(AssinaturaJaAtivaException.class);

        verify(gatewayPagamento, never()).cobrar(any(), any(), anyLong(), any(), any());
    }

    @Test
    void dadoPlanoInexistenteOuInativo_quandoAssina_entaoPlanoNaoEncontrado() {
        when(planoService.buscarAtivoPorCodigo("OUTRO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR, "OUTRO", TOKEN))
                .isInstanceOf(PlanoNaoEncontradoException.class);

        verify(assinaturaGoldService, never()).buscarPorUsuario(any());
    }

    @Test
    void dadoPapelAtivoEmpregador_quandoAssina_entaoRecusaSemConsultarNada() {
        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR, Plano.CODIGO_GOLD, TOKEN))
                .isInstanceOf(SomentePrestadorAssinaGoldException.class);

        verify(travaTransacional, never()).travar(any(), any());
        verify(planoService, never()).buscarAtivoPorCodigo(any());
    }

    @Test
    void dadoAssinaturaCanceladaNoMesmoUsuario_quandoAssinaDeNovo_entaoReativaAMesmaLinha() {
        AssinaturaGold cancelada = AssinaturaGold.nova(USUARIO_ID, PLANO_ID, AGORA.minusSeconds(7200),
                AGORA.minusSeconds(3600));
        ReflectionTestUtils.setField(cancelada, "status", StatusAssinatura.CANCELADA);
        UUID idExistente = UUID.randomUUID();
        ReflectionTestUtils.setField(cancelada, "id", idExistente);

        when(planoService.buscarAtivoPorCodigo(Plano.CODIGO_GOLD)).thenReturn(Optional.of(planoGold()));
        when(assinaturaGoldService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(cancelada));
        when(gatewayPagamento.cobrar(eq(USUARIO_ID), eq(TOKEN), eq(2990L), eq("BRL"), anyString()))
                .thenReturn(new ResultadoCobranca(true, "SIM-999", "VISA", "1111"));
        when(assinaturaGoldService.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));
        when(cobrancaService.salvar(any())).thenAnswer(chamada -> {
            Cobranca c = chamada.getArgument(0);
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            return c;
        });
        when(documentoFiscalService.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));

        AssinaturaGold resultado = useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR, Plano.CODIGO_GOLD, TOKEN);

        assertThat(resultado.getId()).isEqualTo(idExistente);
        assertThat(resultado.estaAtiva()).isTrue();
    }
}
