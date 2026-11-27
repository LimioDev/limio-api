package com.limio.api.modulos.financeiro.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.financeiro.actions.helper.ResolverCobrancaGoldHelper;
import com.limio.api.modulos.financeiro.actions.service.GatewayPagamento;
import com.limio.api.modulos.financeiro.actions.service.ResultadoCobranca;
import com.limio.api.modulos.financeiro.actions.usecase.RenovarGoldUseCase;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.Cobranca;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.CobrancaService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums.StatusCobranca;
import com.limio.api.modulos.financeiro.assinaturagold.enums.StatusAssinatura;
import com.limio.api.modulos.financeiro.excecao.GatewayIndisponivelException;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamento;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamentoService;
import com.limio.api.modulos.financeiro.plano.Plano;
import com.limio.api.modulos.financeiro.plano.PlanoService;
import com.limio.api.modulos.financeiro.plano.enums.CicloPlano;

@ExtendWith(MockitoExtension.class)
class RenovarGoldUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID PLANO_ID = UUID.randomUUID();
    private static final Instant FIM = Instant.parse("2026-11-26T12:00:00Z");
    private static final Instant AGORA = Instant.parse("2026-11-26T12:15:00Z");
    private static final String TOKEN = "tok_salvo";

    @Mock
    private AssinaturaGoldService assinaturaGoldService;

    @Mock
    private CobrancaService cobrancaService;

    @Mock
    private PlanoService planoService;

    @Mock
    private MeioPagamentoService meioPagamentoService;

    @Mock
    private GatewayPagamento gatewayPagamento;

    @Mock
    private ResolverCobrancaGoldHelper resolverCobranca;

    @Mock
    private TravaTransacional trava;

    private RenovarGoldUseCase useCase;
    private AssinaturaGold assinatura;
    private final Map<UUID, Cobranca> cobrancasSalvas = new HashMap<>();

    @BeforeEach
    void montar() {
        useCase = new RenovarGoldUseCase(assinaturaGoldService, cobrancaService, planoService, meioPagamentoService,
                gatewayPagamento, resolverCobranca, trava, TransactionOperations.withoutTransaction(),
                Clock.fixed(AGORA, ZoneOffset.UTC), 100);

        assinatura = AssinaturaGold.nova(USUARIO_ID, PLANO_ID, FIM.minus(Duration.ofDays(31)), FIM);
        assinatura.confirmarAtiva();
        ReflectionTestUtils.setField(assinatura, "id", UUID.randomUUID());

        when(assinaturaGoldService.buscarIdsParaRenovar(AGORA, 100)).thenReturn(List.of(assinatura.getId()));
        when(assinaturaGoldService.buscarUsuarioId(assinatura.getId())).thenReturn(Optional.of(USUARIO_ID));
        when(assinaturaGoldService.buscarPorId(assinatura.getId())).thenReturn(Optional.of(assinatura));
        lenient().when(assinaturaGoldService.buscarPorIdOuFalhar(assinatura.getId())).thenReturn(assinatura);
    }

    private static Plano planoGold() {
        Plano plano = Plano.builder().codigo(Plano.CODIGO_GOLD).nome("Plano GOLD").precoCentavos(2990).moeda("BRL")
                .ciclo(CicloPlano.MENSAL).ativo(true).build();
        ReflectionTestUtils.setField(plano, "id", PLANO_ID);
        return plano;
    }

    /** Assinatura vencida, sem cobrança pendente, com o cartão salvo; cada teste muda o que precisa. */
    private void prepararCobranca(Optional<MeioPagamento> cartao) {
        when(cobrancaService.buscarPendente(assinatura.getId())).thenReturn(Optional.empty());
        when(planoService.buscarPorIdOuFalhar(PLANO_ID)).thenReturn(planoGold());
        when(cobrancaService.contarRecusadasDoCiclo(assinatura.getId(), FIM)).thenReturn(1);
        when(meioPagamentoService.buscarPorUsuario(USUARIO_ID)).thenReturn(cartao);
        when(cobrancaService.salvar(any())).thenAnswer(chamada -> {
            Cobranca cobranca = chamada.getArgument(0);
            if (cobranca.getId() == null) {
                ReflectionTestUtils.setField(cobranca, "id", UUID.randomUUID());
            }
            cobrancasSalvas.put(cobranca.getId(), cobranca);
            return cobranca;
        });
        lenient().when(cobrancaService.buscarPorIdOuFalhar(any()))
                .thenAnswer(chamada -> cobrancasSalvas.get(chamada.<UUID>getArgument(0)));
    }

    private static Optional<MeioPagamento> cartaoSalvo() {
        return Optional.of(MeioPagamento.novo(USUARIO_ID, TOKEN, "VISA", "1234"));
    }

    private Cobranca cobrancaReservada() {
        ArgumentCaptor<Cobranca> captor = ArgumentCaptor.forClass(Cobranca.class);
        verify(cobrancaService).salvar(captor.capture());
        return captor.getValue();
    }

    @Test
    void dadoCartaoSalvoEAprovado_quandoExecuta_entaoCobraASegundaTentativaDoCicloEAprova() {
        prepararCobranca(cartaoSalvo());
        when(gatewayPagamento.cobrar(eq(USUARIO_ID), eq(TOKEN), eq(2990L), eq("BRL"), anyString()))
                .thenReturn(new ResultadoCobranca(true, "pay_1", "VISA", "1234"));

        useCase.executar();

        Cobranca cobranca = cobrancaReservada();
        assertThat(cobranca.ehRenovacao()).isTrue();
        assertThat(cobranca.getInicioCicloCobrado()).isEqualTo(FIM);
        assertThat(cobranca.getTentativa()).isEqualTo(2);
        verify(trava, org.mockito.Mockito.times(2)).travar(EscopoTrava.ASSINATURA_GOLD, USUARIO_ID.toString());
        verify(resolverCobranca).aprovar(assinatura, cobranca, "pay_1", AGORA);
    }

    @Test
    void dadoGatewayRecusa_quandoExecuta_entaoRegistraARecusa() {
        prepararCobranca(cartaoSalvo());
        when(gatewayPagamento.cobrar(any(), any(), any(Long.class), any(), any()))
                .thenReturn(ResultadoCobranca.recusada());

        useCase.executar();

        Cobranca cobranca = cobrancaReservada();
        verify(resolverCobranca).recusar(assinatura, cobranca, AGORA);
    }

    @Test
    void dadoSemCartaoSalvo_quandoExecuta_entaoRecusaSemChamarOGateway() {
        prepararCobranca(Optional.empty());

        useCase.executar();

        verify(gatewayPagamento, never()).cobrar(any(), any(), any(Long.class), any(), any());
        Cobranca cobranca = cobrancaReservada();
        verify(resolverCobranca).recusar(assinatura, cobranca, AGORA);
    }

    @Test
    void dadoGatewaySemResposta_quandoExecuta_entaoRegistraFalha() {
        prepararCobranca(cartaoSalvo());
        when(gatewayPagamento.cobrar(any(), any(), any(Long.class), any(), any()))
                .thenThrow(new GatewayIndisponivelException());

        useCase.executar();

        Cobranca cobranca = cobrancaReservada();
        verify(resolverCobranca).registrarFalha(assinatura, cobranca, AGORA);
    }

    @Test
    void dadoAvisoDoGatewayResolveuAntesDaConfirmacao_quandoExecuta_entaoNaoAplicaDuasVezes() {
        prepararCobranca(cartaoSalvo());
        when(gatewayPagamento.cobrar(any(), any(), any(Long.class), any(), any())).thenAnswer(chamada -> {
            cobrancasSalvas.values().forEach(cobranca -> cobranca.confirmarAprovada("pay_webhook"));
            return new ResultadoCobranca(true, "pay_1", "VISA", "1234");
        });

        useCase.executar();

        verify(resolverCobranca, never()).aprovar(any(), any(), any(), any());
    }

    @Test
    void dadoRenovacaoCancelada_quandoExecuta_entaoEncerraSemCobrar() {
        ReflectionTestUtils.setField(assinatura, "renovacaoCanceladaEm", FIM.minus(Duration.ofDays(5)));

        useCase.executar();

        assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.ENCERRADA);
        assertThat(assinatura.getEncerradaEm()).isEqualTo(AGORA);
        verify(assinaturaGoldService).salvar(assinatura);
        verify(cobrancaService, never()).salvar(any());
        verify(gatewayPagamento, never()).cobrar(any(), any(), any(Long.class), any(), any());
    }

    @Test
    void dadoNovaTentativaMarcadaParaDepois_quandoExecuta_entaoNaoFazNada() {
        assinatura.agendarNovaTentativa(AGORA.plusSeconds(60));

        useCase.executar();

        verify(cobrancaService, never()).salvar(any());
        verify(assinaturaGoldService, never()).salvar(any());
    }

    @Test
    void dadoCobrancaAnteriorSemRespostaRecente_quandoExecuta_entaoEsperaOAvisoDoGateway() {
        Cobranca pendente = Cobranca.renovacao(assinatura.getId(), 2990, "chave", FIM, 1);
        Instant criadaEm = AGORA.minus(Duration.ofHours(2));
        ReflectionTestUtils.setField(pendente, "criadoEm", criadaEm);
        when(cobrancaService.buscarPendente(assinatura.getId())).thenReturn(Optional.of(pendente));
        when(resolverCobranca.proximaTentativa(criadaEm)).thenReturn(criadaEm.plus(Duration.ofDays(1)));

        useCase.executar();

        assertThat(assinatura.getProximaTentativaEm()).isEqualTo(criadaEm.plus(Duration.ofDays(1)));
        assertThat(pendente.getStatus()).isEqualTo(StatusCobranca.PENDENTE);
        verify(gatewayPagamento, never()).cobrar(any(), any(), any(Long.class), any(), any());
    }

    @Test
    void dadoCobrancaAnteriorSemRespostaAntiga_quandoExecuta_entaoMarcaFalhaECobraDeNovo() {
        Cobranca pendente = Cobranca.renovacao(assinatura.getId(), 2990, "chave", FIM, 1);
        ReflectionTestUtils.setField(pendente, "id", UUID.randomUUID());
        Instant criadaEm = AGORA.minus(Duration.ofDays(2));
        ReflectionTestUtils.setField(pendente, "criadoEm", criadaEm);
        prepararCobranca(cartaoSalvo());
        when(cobrancaService.buscarPendente(assinatura.getId())).thenReturn(Optional.of(pendente));
        when(resolverCobranca.proximaTentativa(criadaEm)).thenReturn(criadaEm.plus(Duration.ofDays(1)));
        when(gatewayPagamento.cobrar(any(), any(), any(Long.class), any(), any()))
                .thenReturn(new ResultadoCobranca(true, "pay_2", "VISA", "1234"));

        useCase.executar();

        assertThat(pendente.getStatus()).isEqualTo(StatusCobranca.FALHOU);
        verify(resolverCobranca).aprovar(any(), any(), eq("pay_2"), eq(AGORA));
    }

    @Test
    void dadoErroNumaAssinatura_quandoExecuta_entaoAsOutrasContinuam() {
        UUID outra = UUID.randomUUID();
        when(assinaturaGoldService.buscarIdsParaRenovar(AGORA, 100)).thenReturn(List.of(outra, assinatura.getId()));
        when(assinaturaGoldService.buscarUsuarioId(outra)).thenThrow(new IllegalStateException("banco"));
        ReflectionTestUtils.setField(assinatura, "renovacaoCanceladaEm", FIM);

        useCase.executar();

        assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.ENCERRADA);
    }
}
