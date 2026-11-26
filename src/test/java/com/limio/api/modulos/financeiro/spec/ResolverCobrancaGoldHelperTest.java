package com.limio.api.modulos.financeiro.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.eventos.CobrancaGoldAprovadaEvent;
import com.limio.api.comum.eventos.CobrancaGoldRecusadaEvent;
import com.limio.api.modulos.financeiro.actions.helper.ResolverCobrancaGoldHelper;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.Cobranca;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.CobrancaService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal.DocumentoFiscalService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums.StatusCobranca;
import com.limio.api.modulos.financeiro.assinaturagold.enums.StatusAssinatura;
import com.limio.api.modulos.financeiro.eventos.GoldAssinadoEvent;
import com.limio.api.modulos.financeiro.plano.Plano;
import com.limio.api.modulos.financeiro.plano.PlanoService;
import com.limio.api.modulos.financeiro.plano.enums.CicloPlano;

@ExtendWith(MockitoExtension.class)
class ResolverCobrancaGoldHelperTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID PLANO_ID = UUID.randomUUID();
    private static final Instant INICIO = Instant.parse("2026-10-26T12:00:00Z");
    private static final Instant FIM = Instant.parse("2026-11-26T12:00:00Z");
    private static final Instant AGORA = Instant.parse("2026-11-26T12:15:00Z");
    private static final Duration INTERVALO = Duration.ofDays(1);

    @Mock
    private AssinaturaGoldService assinaturaGoldService;

    @Mock
    private CobrancaService cobrancaService;

    @Mock
    private DocumentoFiscalService documentoFiscalService;

    @Mock
    private PlanoService planoService;

    @Mock
    private ApplicationEventPublisher eventos;

    private ResolverCobrancaGoldHelper helper;

    @BeforeEach
    void montar() {
        helper = new ResolverCobrancaGoldHelper(assinaturaGoldService, cobrancaService, documentoFiscalService,
                planoService, eventos, 3, INTERVALO);
    }

    private static Plano planoGold() {
        Plano plano = Plano.builder().codigo(Plano.CODIGO_GOLD).nome("Plano GOLD").precoCentavos(2990).moeda("BRL")
                .ciclo(CicloPlano.MENSAL).ativo(true).build();
        ReflectionTestUtils.setField(plano, "id", PLANO_ID);
        return plano;
    }

    private static AssinaturaGold assinaturaAtiva() {
        AssinaturaGold assinatura = AssinaturaGold.nova(USUARIO_ID, PLANO_ID, INICIO, FIM);
        assinatura.confirmarAtiva();
        ReflectionTestUtils.setField(assinatura, "id", UUID.randomUUID());
        return assinatura;
    }

    private static Cobranca renovacaoDe(AssinaturaGold assinatura, Instant inicioCicloCobrado, int tentativa) {
        Cobranca cobranca = Cobranca.renovacao(assinatura.getId(), 2990, UUID.randomUUID().toString(),
                inicioCicloCobrado, tentativa);
        ReflectionTestUtils.setField(cobranca, "id", UUID.randomUUID());
        return cobranca;
    }

    private void devolverRecibo() {
        when(documentoFiscalService.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void dadoRenovacaoDoCicloEmAberto_quandoAprova_entaoAvancaOCicloEmiteReciboEAvisa() {
        AssinaturaGold assinatura = assinaturaAtiva();
        Cobranca cobranca = renovacaoDe(assinatura, FIM, 1);
        when(planoService.buscarPorIdOuFalhar(PLANO_ID)).thenReturn(planoGold());
        devolverRecibo();

        helper.aprovar(assinatura, cobranca, "pay_123", AGORA);

        Instant novoFim = FIM.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.APROVADA);
        assertThat(cobranca.getGatewayCobrancaId()).isEqualTo("pay_123");
        assertThat(assinatura.getDataInicioCiclo()).isEqualTo(FIM);
        assertThat(assinatura.getDataFimCiclo()).isEqualTo(novoFim);
        assertThat(assinatura.getProximaTentativaEm()).isNull();
        verify(documentoFiscalService).salvar(any());

        ArgumentCaptor<CobrancaGoldAprovadaEvent> evento = ArgumentCaptor.forClass(CobrancaGoldAprovadaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().usuarioId()).isEqualTo(USUARIO_ID);
        assertThat(evento.getValue().valorCentavos()).isEqualTo(2990);
        assertThat(evento.getValue().proximaCobrancaEm()).isEqualTo(novoFim);
    }

    @Test
    void dadoRenovacaoDeUmCicloJaRenovado_quandoAprova_entaoSoRegistraACobranca() {
        AssinaturaGold assinatura = assinaturaAtiva();
        Cobranca atrasada = renovacaoDe(assinatura, INICIO, 1);

        helper.aprovar(assinatura, atrasada, "pay_velho", AGORA);

        assertThat(atrasada.getStatus()).isEqualTo(StatusCobranca.APROVADA);
        assertThat(assinatura.getDataFimCiclo()).isEqualTo(FIM);
        verify(documentoFiscalService, never()).salvar(any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void dadoCobrancaDaAssinaturaPendente_quandoAprova_entaoAtivaOGold() {
        AssinaturaGold assinatura = AssinaturaGold.nova(USUARIO_ID, PLANO_ID, INICIO, FIM);
        ReflectionTestUtils.setField(assinatura, "id", UUID.randomUUID());
        Cobranca cobranca = Cobranca.pendente(assinatura.getId(), 2990, "chave");
        when(planoService.buscarPorIdOuFalhar(PLANO_ID)).thenReturn(planoGold());
        devolverRecibo();

        helper.aprovar(assinatura, cobranca, "pay_1", AGORA);

        assertThat(assinatura.estaAtiva()).isTrue();
        assertThat(assinatura.getDataFimCiclo()).isEqualTo(FIM);
        verify(eventos).publishEvent(any(GoldAssinadoEvent.class));
    }

    @Test
    void dadoRecusaComTentativasSobrando_quandoRecusa_entaoAgendaNovaTentativaEContinuaGold() {
        AssinaturaGold assinatura = assinaturaAtiva();
        Cobranca cobranca = renovacaoDe(assinatura, FIM, 1);
        when(cobrancaService.contarRecusadasDoCiclo(assinatura.getId(), FIM)).thenReturn(1);

        helper.recusar(assinatura, cobranca, AGORA);

        assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.RECUSADA);
        assertThat(assinatura.estaAtiva()).isTrue();
        assertThat(assinatura.getProximaTentativaEm()).isEqualTo(AGORA.plus(INTERVALO));

        ArgumentCaptor<CobrancaGoldRecusadaEvent> evento = ArgumentCaptor.forClass(CobrancaGoldRecusadaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().tentativa()).isEqualTo(1);
        assertThat(evento.getValue().proximaTentativaEm()).isEqualTo(AGORA.plus(INTERVALO));
        assertThat(evento.getValue().voltouParaFree()).isFalse();
    }

    @Test
    void dadoUltimaTentativa_quandoRecusa_entaoEncerraEVoltaParaFree() {
        AssinaturaGold assinatura = assinaturaAtiva();
        Cobranca cobranca = renovacaoDe(assinatura, FIM, 3);
        when(cobrancaService.contarRecusadasDoCiclo(assinatura.getId(), FIM)).thenReturn(3);

        helper.recusar(assinatura, cobranca, AGORA);

        assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.ENCERRADA);
        assertThat(assinatura.getEncerradaEm()).isEqualTo(AGORA);
        assertThat(assinatura.getProximaTentativaEm()).isNull();

        ArgumentCaptor<CobrancaGoldRecusadaEvent> evento = ArgumentCaptor.forClass(CobrancaGoldRecusadaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().tentativa()).isEqualTo(3);
        assertThat(evento.getValue().voltouParaFree()).isTrue();
    }

    @Test
    void dadoRecusaDeUmCicloQueNaoEstaMaisEmAberto_quandoRecusa_entaoNaoMexeNaAssinatura() {
        AssinaturaGold assinatura = assinaturaAtiva();
        assinatura.encerrar(AGORA);
        Cobranca cobranca = renovacaoDe(assinatura, FIM, 1);

        helper.recusar(assinatura, cobranca, AGORA);

        assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.RECUSADA);
        assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.ENCERRADA);
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void dadoCobrancaDaAssinaturaPendente_quandoRecusa_entaoCancelaAPendenciaSemAvisar() {
        AssinaturaGold assinatura = AssinaturaGold.nova(USUARIO_ID, PLANO_ID, INICIO, FIM);
        Cobranca cobranca = Cobranca.pendente(UUID.randomUUID(), 2990, "chave");

        helper.recusar(assinatura, cobranca, AGORA);

        assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.CANCELADA);
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void dadoGatewaySemResposta_quandoRegistraFalha_entaoAgendaSemContarRecusaNemAvisar() {
        AssinaturaGold assinatura = assinaturaAtiva();
        Cobranca cobranca = renovacaoDe(assinatura, FIM, 1);

        helper.registrarFalha(assinatura, cobranca, AGORA);

        assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.FALHOU);
        assertThat(cobranca.aguardaResultado()).isTrue();
        assertThat(assinatura.estaAtiva()).isTrue();
        assertThat(assinatura.getProximaTentativaEm()).isEqualTo(AGORA.plus(INTERVALO));
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void dadoMaxTentativasZero_quandoMonta_entaoRecusaAConfiguracao() {
        assertThatThrownBy(() -> new ResolverCobrancaGoldHelper(assinaturaGoldService, cobrancaService,
                documentoFiscalService, planoService, eventos, 0, INTERVALO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
