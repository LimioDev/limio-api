package com.limio.api.modulos.financeiro.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.CobrancaGoldAprovadaEvent;
import com.limio.api.comum.eventos.CobrancaGoldRecusadaEvent;
import com.limio.api.comum.portas.ConsultaPlanoPrestador;
import com.limio.api.comum.portas.enums.Plano;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.financeiro.actions.usecase.AssinarGoldUseCase;
import com.limio.api.modulos.financeiro.actions.usecase.RenovarGoldUseCase;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldRepository;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.Cobranca;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.CobrancaRepository;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal.DocumentoFiscalRepository;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums.StatusCobranca;
import com.limio.api.modulos.financeiro.assinaturagold.enums.StatusAssinatura;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamento;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamentoRepository;
import com.limio.api.modulos.financeiro.plano.PlanoRepository;

/**
 * BDD da renovação mensal do GOLD (ETI-85) contra o banco real, com o gateway simulado: o token salvo do Prestador
 * decide o resultado ({@code tok_recusado} recusa, {@code tok_indisponivel} não responde, qualquer outro aprova). O
 * "agora" é um {@link Clock} controlado e a rotina é chamada direto ({@code financeiro.gold.renovacao.cron=-} nos
 * testes). Padrões: 3 tentativas, 1 dia entre elas.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class RenovarGoldStoryTest {

    private static final String TOKEN_APROVADO = "tok_qualquer";
    private static final String TOKEN_RECUSADO = "tok_recusado";
    private static final String TOKEN_INDISPONIVEL = "tok_indisponivel";
    private static final Duration INTERVALO = Duration.ofDays(1);

    @Autowired
    private RenovarGoldUseCase renovarGold;

    @Autowired
    private AssinarGoldUseCase assinarGold;

    @Autowired
    private ConsultaPlanoPrestador consultaPlano;

    @Autowired
    private PlanoRepository planoRepository;

    @Autowired
    private AssinaturaGoldRepository assinaturaGoldRepository;

    @Autowired
    private CobrancaRepository cobrancaRepository;

    @Autowired
    private DocumentoFiscalRepository documentoFiscalRepository;

    @Autowired
    private MeioPagamentoRepository meioPagamentoRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents eventos;

    @MockitoBean
    private Clock relogio;

    private Instant agora;
    private Instant fimDoCiclo;
    private UUID prestadorId;

    @BeforeEach
    void prepararAssinaturaVencida() {
        limparBase();
        agora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        when(relogio.instant()).thenAnswer(chamada -> agora);
        when(relogio.getZone()).thenReturn(ZoneOffset.UTC);
        prestadorId = UUID.randomUUID();
        fimDoCiclo = agora.minus(Duration.ofMinutes(10));
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private void limparBase() {
        documentoFiscalRepository.deleteAll();
        cobrancaRepository.deleteAll();
        assinaturaGoldRepository.deleteAll();
        meioPagamentoRepository.deleteAll();
    }

    private AssinaturaGold assinaturaAtiva(String tokenSalvo) {
        UUID planoId = planoRepository.findByCodigoAndAtivoTrue(
                com.limio.api.modulos.financeiro.plano.Plano.CODIGO_GOLD).orElseThrow().getId();
        AssinaturaGold assinatura = AssinaturaGold.nova(prestadorId, planoId, fimDoCiclo.minus(Duration.ofDays(30)),
                fimDoCiclo);
        assinatura.confirmarAtiva();
        if (tokenSalvo != null) {
            meioPagamentoRepository.save(MeioPagamento.novo(prestadorId, tokenSalvo, "VISA", "4242"));
        }
        return assinaturaGoldRepository.save(assinatura);
    }

    private AssinaturaGold recarregar(AssinaturaGold assinatura) {
        return assinaturaGoldRepository.findById(assinatura.getId()).orElseThrow();
    }

    private List<Cobranca> cobrancas() {
        return cobrancaRepository.findAll().stream().sorted(Comparator.comparing(Cobranca::getTentativa)).toList();
    }

    private void trocarCartao(String token) {
        jdbc.update("update meio_pagamento set gateway_token = ? where usuario_id = ?", token, prestadorId);
    }

    private void passarParaAProximaTentativa() {
        agora = agora.plus(INTERVALO).plusSeconds(1);
    }

    private static Instant umMesDepois(Instant instante) {
        return instante.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
    }

    @Test
    void dadoCicloVencido_quandoRenovacaoAprovada_entaoAvancaOCicloGravaACobrancaEOReciboEAvisa() {
        AssinaturaGold assinatura = assinaturaAtiva(TOKEN_APROVADO);
        Instant fimAnterior = recarregar(assinatura).getDataFimCiclo();

        renovarGold.executar();

        AssinaturaGold renovada = recarregar(assinatura);
        assertThat(renovada.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(renovada.getDataInicioCiclo()).isEqualTo(fimAnterior);
        assertThat(renovada.getDataFimCiclo()).isEqualTo(umMesDepois(fimAnterior));

        List<Cobranca> cobrancas = cobrancas();
        assertThat(cobrancas).singleElement().satisfies(cobranca -> {
            assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.APROVADA);
            assertThat(cobranca.getValorCentavos()).isEqualTo(2990);
            assertThat(cobranca.getTentativa()).isEqualTo(1);
            assertThat(cobranca.getInicioCicloCobrado()).isEqualTo(fimAnterior);
        });
        assertThat(documentoFiscalRepository.count()).isEqualTo(1);

        assertThat(eventos.stream(CobrancaGoldAprovadaEvent.class)).singleElement().satisfies(evento -> {
            assertThat(evento.usuarioId()).isEqualTo(prestadorId);
            assertThat(evento.valorCentavos()).isEqualTo(2990);
            assertThat(evento.proximaCobrancaEm()).isEqualTo(umMesDepois(fimAnterior));
        });

        renovarGold.executar();
        assertThat(cobrancaRepository.count()).as("ciclo novo ainda vigente: nada a cobrar").isEqualTo(1);
    }

    @Test
    void dadoRenovacaoRecusada_quandoANovaTentativaAprova_entaoOGoldContinua() {
        AssinaturaGold assinatura = assinaturaAtiva(TOKEN_RECUSADO);
        Instant fimAnterior = recarregar(assinatura).getDataFimCiclo();

        renovarGold.executar();

        AssinaturaGold aguardando = recarregar(assinatura);
        assertThat(aguardando.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(aguardando.getProximaTentativaEm()).isEqualTo(agora.plus(INTERVALO));
        assertThat(consultaPlano.planoAtual(prestadorId)).isEqualTo(Plano.GOLD);
        assertThat(cobrancas()).singleElement().satisfies(cobranca -> {
            assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.RECUSADA);
            assertThat(cobranca.getTentativa()).isEqualTo(1);
        });
        assertThat(eventos.stream(CobrancaGoldRecusadaEvent.class)).singleElement().satisfies(evento -> {
            assertThat(evento.tentativa()).isEqualTo(1);
            assertThat(evento.proximaTentativaEm()).isEqualTo(agora.plus(INTERVALO));
        });

        renovarGold.executar();
        assertThat(cobrancaRepository.count()).as("antes da hora marcada não tenta de novo").isEqualTo(1);

        trocarCartao(TOKEN_APROVADO);
        passarParaAProximaTentativa();
        renovarGold.executar();

        AssinaturaGold renovada = recarregar(assinatura);
        assertThat(renovada.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(renovada.getDataInicioCiclo()).isEqualTo(fimAnterior);
        assertThat(renovada.getDataFimCiclo()).isEqualTo(umMesDepois(fimAnterior));
        assertThat(renovada.getProximaTentativaEm()).isNull();
        assertThat(cobrancas()).extracting(Cobranca::getTentativa, Cobranca::getStatus)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, StatusCobranca.RECUSADA),
                        org.assertj.core.groups.Tuple.tuple(2, StatusCobranca.APROVADA));
        assertThat(eventos.stream(CobrancaGoldAprovadaEvent.class)).hasSize(1);
    }

    @Test
    void dadoTodasAsTentativasRecusadas_entaoVoltaParaFreeEPodeAssinarDeNovo() {
        AssinaturaGold assinatura = assinaturaAtiva(TOKEN_RECUSADO);

        renovarGold.executar();
        passarParaAProximaTentativa();
        renovarGold.executar();
        assertThat(consultaPlano.planoAtual(prestadorId)).isEqualTo(Plano.GOLD);
        passarParaAProximaTentativa();
        renovarGold.executar();

        AssinaturaGold encerrada = recarregar(assinatura);
        assertThat(encerrada.getStatus()).isEqualTo(StatusAssinatura.ENCERRADA);
        assertThat(encerrada.getEncerradaEm()).isEqualTo(agora);
        assertThat(encerrada.getProximaTentativaEm()).isNull();
        assertThat(consultaPlano.planoAtual(prestadorId)).isEqualTo(Plano.FREE);
        assertThat(cobrancas()).extracting(Cobranca::getTentativa).containsExactly(1, 2, 3);
        assertThat(cobrancas()).allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(StatusCobranca.RECUSADA));
        assertThat(eventos.stream(CobrancaGoldRecusadaEvent.class))
                .extracting(CobrancaGoldRecusadaEvent::tentativa, CobrancaGoldRecusadaEvent::voltouParaFree)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, false),
                        org.assertj.core.groups.Tuple.tuple(2, false), org.assertj.core.groups.Tuple.tuple(3, true));

        passarParaAProximaTentativa();
        renovarGold.executar();
        assertThat(cobrancaRepository.count()).as("assinatura encerrada não é mais cobrada").isEqualTo(3);

        AssinaturaGold reassinada = assinarGold.executar(prestadorId, PapelUsuario.PRESTADOR,
                com.limio.api.modulos.financeiro.plano.Plano.CODIGO_GOLD, TOKEN_APROVADO);
        assertThat(reassinada.getId()).isEqualTo(assinatura.getId());
        AssinaturaGold ativaDeNovo = recarregar(assinatura);
        assertThat(ativaDeNovo.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(ativaDeNovo.getEncerradaEm()).isNull();
        assertThat(consultaPlano.planoAtual(prestadorId)).isEqualTo(Plano.GOLD);
    }

    @Test
    void dadoRenovacaoCancelada_quandoOCicloTermina_entaoVoltaParaFreeSemCobrar() {
        AssinaturaGold assinatura = assinaturaAtiva(TOKEN_APROVADO);
        jdbc.update("update assinatura_gold set renovacao_cancelada_em = ? where id = ?",
                java.sql.Timestamp.from(fimDoCiclo.minus(Duration.ofDays(3))), assinatura.getId());

        renovarGold.executar();

        AssinaturaGold encerrada = recarregar(assinatura);
        assertThat(encerrada.getStatus()).isEqualTo(StatusAssinatura.ENCERRADA);
        assertThat(encerrada.getEncerradaEm()).isEqualTo(agora);
        assertThat(consultaPlano.planoAtual(prestadorId)).isEqualTo(Plano.FREE);
        assertThat(cobrancaRepository.count()).isZero();
        assertThat(eventos.stream(CobrancaGoldAprovadaEvent.class)).isEmpty();
        assertThat(eventos.stream(CobrancaGoldRecusadaEvent.class)).isEmpty();
    }

    @Test
    void dadoRenovacaoCanceladaComOCicloAindaVigente_entaoContinuaGold() {
        fimDoCiclo = agora.plus(Duration.ofDays(5));
        AssinaturaGold assinatura = assinaturaAtiva(TOKEN_APROVADO);
        jdbc.update("update assinatura_gold set renovacao_cancelada_em = ? where id = ?",
                java.sql.Timestamp.from(agora), assinatura.getId());

        renovarGold.executar();

        assertThat(recarregar(assinatura).getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(consultaPlano.planoAtual(prestadorId)).isEqualTo(Plano.GOLD);
    }

    @Test
    void dadoGatewaySemResposta_entaoTentaDeNovoSemContarRecusaNemAvisar() {
        AssinaturaGold assinatura = assinaturaAtiva(TOKEN_INDISPONIVEL);

        renovarGold.executar();

        AssinaturaGold aguardando = recarregar(assinatura);
        assertThat(aguardando.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(aguardando.getProximaTentativaEm()).isEqualTo(agora.plus(INTERVALO));
        assertThat(cobrancas()).singleElement()
                .satisfies(cobranca -> assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.FALHOU));
        assertThat(eventos.stream(CobrancaGoldRecusadaEvent.class)).isEmpty();

        trocarCartao(TOKEN_RECUSADO);
        passarParaAProximaTentativa();
        renovarGold.executar();

        assertThat(eventos.stream(CobrancaGoldRecusadaEvent.class)).singleElement()
                .satisfies(evento -> assertThat(evento.tentativa()).as("a falha do gateway não conta").isEqualTo(1));
    }

    @Test
    void dadoSemCartaoSalvo_entaoARenovacaoERecusada() {
        AssinaturaGold assinatura = assinaturaAtiva(null);

        renovarGold.executar();

        assertThat(cobrancas()).singleElement()
                .satisfies(cobranca -> assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.RECUSADA));
        assertThat(recarregar(assinatura).getProximaTentativaEm()).isEqualTo(agora.plus(INTERVALO));
    }
}
