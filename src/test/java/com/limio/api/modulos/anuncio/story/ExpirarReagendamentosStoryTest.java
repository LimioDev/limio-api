package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.ReagendamentoExpiradoEvent;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.actions.usecase.ExpirarReagendamentosUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.IniciarServicoUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoRepository;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/**
 * BDD do job de expiração das propostas de reagendamento (ETI-99, US47), contra Postgres real. O job usa o relógio
 * real, então as propostas são vencidas ou não conforme o {@code expiraEm} gravado no passado ou no futuro. Sem HTTP:
 * a story chama {@code executar()} direto (o cron fica desligado nos testes). As propostas entram por repositório; as
 * corridas usam threads reais e conferem o ESTADO FINAL do banco.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class ExpirarReagendamentosStoryTest {

    private static final Duration UM_DIA = Duration.ofDays(1);

    @Autowired
    private ExpirarReagendamentosUseCase job;

    @Autowired
    private IniciarServicoUseCase iniciarServico;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    @Autowired
    private ReagendamentoRepository reagendamentoRepository;

    @MockitoSpyBean
    private ReagendamentoService reagendamentoService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents eventos;

    @BeforeEach
    void limparBase() {
        jdbc.execute("delete from log_auditoria");
        reagendamentoRepository.deleteAll();
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    // ---------------------------------------------------------------- cenário

    /** Serviço combinado: anúncio {@code PUBLICADO} com data vigente daqui a 3 dias e o Prestador {@code ESCOLHIDO}. */
    private Anuncio combinado() {
        Anuncio anuncio = anuncioRepository.save(Anuncio.builder()
                .contratanteId(UUID.randomUUID()) // sem FK pra usuário (ADR-0001 §1.6)
                .titulo("Instalar 3 tomadas")
                .categoriaId(UUID.randomUUID())
                .descricao("Instalar 3 tomadas novas na sala e trocar o disjuntor")
                .dataHoraPretendida(Instant.now().plus(UM_DIA.multipliedBy(3)).truncatedTo(ChronoUnit.MICROS))
                .bairroRegiaoSnapshot("Pinheiros")
                .status(StatusAnuncio.PUBLICADO)
                .build());
        aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(UUID.randomUUID())
                .status(StatusAplicacao.ESCOLHIDA)
                .dataAplicacao(Instant.now().minus(UM_DIA.multipliedBy(2)))
                .escolhidaEm(Instant.now().minus(UM_DIA))
                .build());
        return anuncio;
    }

    private UUID prestadorDe(Anuncio anuncio) {
        return aplicacaoRepository.findAll().stream()
                .filter(a -> a.getAnuncioId().equals(anuncio.getId()))
                .findFirst().orElseThrow().getPrestadorId();
    }

    private Reagendamento proposta(Anuncio anuncio, StatusReagendamento status, Instant expiraEm) {
        return reagendamentoRepository.save(Reagendamento.builder()
                .anuncioId(anuncio.getId())
                .dataAnterior(anuncio.getDataHoraPretendida())
                .dataProposta(anuncio.getDataHoraPretendida().plus(UM_DIA.multipliedBy(2)))
                .justificativa("Imprevisto de agenda")
                .status(status)
                .respondidoEm(status == StatusReagendamento.PENDENTE ? null
                        : Instant.now().minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MICROS))
                .proponenteId(anuncio.getContratanteId())
                .expiraEm(expiraEm.truncatedTo(ChronoUnit.MICROS))
                .build());
    }

    /** Pendente cujo prazo de 24h terminou há 1h. */
    private Reagendamento vencida(Anuncio anuncio) {
        return proposta(anuncio, StatusReagendamento.PENDENTE, Instant.now().minus(Duration.ofHours(1)));
    }

    private Reagendamento recarregar(Reagendamento proposta) {
        return reagendamentoRepository.findById(proposta.getId()).orElseThrow();
    }

    private Anuncio recarregar(Anuncio anuncio) {
        return anuncioRepository.findById(anuncio.getId()).orElseThrow();
    }

    private long auditorias() {
        return jdbc.queryForObject("select count(*) from log_auditoria where tipo_acao = 'REAGENDAMENTO_EXPIRADO'",
                Long.class);
    }

    private void assertNaoMudou(Reagendamento proposta, StatusReagendamento statusEsperado) {
        Reagendamento atual = recarregar(proposta);
        assertThat(atual.getStatus()).isEqualTo(statusEsperado);
        assertThat(atual.getRespondidoEm()).isEqualTo(proposta.getRespondidoEm());
    }

    // ---------------------------------------------------------------- expira

    @Test
    void dadaPropostaPendenteVencida_quandoRodaOJob_entaoExpiraMantemADataOriginalAuditaEPublicaUmEvento() {
        Anuncio anuncio = combinado();
        Reagendamento proposta = vencida(anuncio);

        job.executar();

        Reagendamento depois = recarregar(proposta);
        assertThat(depois.getStatus()).isEqualTo(StatusReagendamento.EXPIRADO);
        assertThat(depois.getRespondidoEm()).isAfter(proposta.getExpiraEm());
        // a data original continua vigente e o serviço continua combinado
        assertThat(recarregar(anuncio).getDataHoraPretendida()).isEqualTo(anuncio.getDataHoraPretendida());
        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(auditorias()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select ip from log_auditoria where tipo_acao = 'REAGENDAMENTO_EXPIRADO'", String.class)).isNull();
        List<ReagendamentoExpiradoEvent> publicados = eventos.stream(ReagendamentoExpiradoEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).reagendamentoId()).isEqualTo(proposta.getId());
        assertThat(publicados.get(0).contratanteId()).isEqualTo(anuncio.getContratanteId());
        assertThat(publicados.get(0).prestadorId()).isEqualTo(prestadorDe(anuncio));
        assertThat(publicados.get(0).proponenteId()).isEqualTo(anuncio.getContratanteId());
        assertThat(publicados.get(0).dataVigente()).isEqualTo(anuncio.getDataHoraPretendida());
    }

    @Test
    void dadaPropostaExpirada_quandoOJobRoda_entaoNaoHaMaisPendenteBloqueandoAConclusaoPorSilencio() {
        Anuncio anuncio = combinado();
        vencida(anuncio);
        assertThat(reagendamentoService.existePendente(anuncio.getId())).isTrue();

        job.executar();

        assertThat(reagendamentoService.existePendente(anuncio.getId())).isFalse();
    }

    @Test
    void dadasVariasPropostasVencidasENaoVencidas_quandoRodaOJob_entaoExpiraSoAsVencidas() {
        Anuncio a = combinado();
        Anuncio b = combinado();
        Anuncio c = combinado();
        Reagendamento vencidaA = vencida(a);
        Reagendamento vencidaB = vencida(b);
        Reagendamento noPrazo = proposta(c, StatusReagendamento.PENDENTE, Instant.now().plus(Duration.ofHours(2)));

        job.executar();

        assertThat(recarregar(vencidaA).getStatus()).isEqualTo(StatusReagendamento.EXPIRADO);
        assertThat(recarregar(vencidaB).getStatus()).isEqualTo(StatusReagendamento.EXPIRADO);
        assertNaoMudou(noPrazo, StatusReagendamento.PENDENTE);
        assertThat(eventos.stream(ReagendamentoExpiradoEvent.class)).hasSize(2);
        assertThat(auditorias()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- não expira

    @Test
    void dadaPropostaAindaDentroDoPrazo_quandoRodaOJob_entaoNaoExpira() {
        Anuncio anuncio = combinado();
        Reagendamento proposta = proposta(anuncio, StatusReagendamento.PENDENTE, Instant.now().plus(Duration.ofHours(1)));

        job.executar();

        assertNaoMudou(proposta, StatusReagendamento.PENDENTE);
        assertThat(eventos.stream(ReagendamentoExpiradoEvent.class)).isEmpty();
        assertThat(auditorias()).isZero();
    }

    @Test
    void dadaPropostaJaRespondidaOuEncerrada_quandoRodaOJob_entaoNuncaExpiraDepois() {
        for (StatusReagendamento encerrado : List.of(StatusReagendamento.ACEITO, StatusReagendamento.RECUSADO,
                StatusReagendamento.CANCELADO, StatusReagendamento.SEM_EFEITO, StatusReagendamento.EXPIRADO)) {
            Anuncio anuncio = combinado();
            // prazo já vencido, mas a proposta foi respondida/encerrada antes do job passar
            Reagendamento proposta = proposta(anuncio, encerrado, Instant.now().minus(Duration.ofHours(1)));

            job.executar();

            assertNaoMudou(proposta, encerrado);
        }
        assertThat(eventos.stream(ReagendamentoExpiradoEvent.class)).isEmpty();
        assertThat(auditorias()).isZero();
    }

    @Test
    void dadoJobRodadoDuasVezes_quandoRodaDeNovo_entaoNaoReexpiraNemPublicaOutraVez() {
        Anuncio anuncio = combinado();
        Reagendamento proposta = vencida(anuncio);

        job.executar();
        Instant expiradaNaPrimeira = recarregar(proposta).getRespondidoEm();
        job.executar();

        assertThat(recarregar(proposta).getRespondidoEm()).isEqualTo(expiradaNaPrimeira);
        assertThat(eventos.stream(ReagendamentoExpiradoEvent.class)).hasSize(1);
        assertThat(auditorias()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- falhas

    @Test
    void dadaFalhaAoGravar_quandoRodaOJob_entaoRollbackSemEventoNemAuditoriaEAPropostaContinuaPendente() {
        Anuncio anuncio = combinado();
        Reagendamento proposta = vencida(anuncio);
        doThrow(new IllegalStateException("falha simulada")).when(reagendamentoService).salvar(any());

        job.executar(); // a falha é logada, não propaga

        assertNaoMudou(proposta, StatusReagendamento.PENDENTE);
        assertThat(eventos.stream(ReagendamentoExpiradoEvent.class)).isEmpty();
        assertThat(auditorias()).isZero();
    }

    @Test
    void dadaFalhaNumaPropostaEDepoisRecuperada_quandoOJobRodaDeNovo_entaoExpira() {
        Anuncio anuncio = combinado();
        Reagendamento proposta = vencida(anuncio);
        doThrow(new IllegalStateException("falha simulada")).when(reagendamentoService).salvar(any());
        job.executar();
        reset(reagendamentoService);

        job.executar();

        assertThat(recarregar(proposta).getStatus()).isEqualTo(StatusReagendamento.EXPIRADO);
        assertThat(eventos.stream(ReagendamentoExpiradoEvent.class)).hasSize(1);
    }

    // ---------------------------------------------------------------- concorrência

    @Test
    void dadoJobEmUmaInstanciaEOutraTambem_quandoRodamAoMesmoTempo_entaoUmaUnicaExpiracaoEUmEvento() throws Exception {
        Anuncio anuncio = combinado();
        Reagendamento proposta = vencida(anuncio);

        rodarEmParalelo(job::executar, job::executar);

        assertThat(recarregar(proposta).getStatus()).isEqualTo(StatusReagendamento.EXPIRADO);
        assertThat(eventos.stream(ReagendamentoExpiradoEvent.class)).hasSize(1);
        assertThat(auditorias()).isEqualTo(1);
    }

    @Test
    void dadoJobEInicioDoServicoAoMesmoTempo_quandoDisputam_entaoSoUmEncerraAPropostaEOEstadoFicaConsistente()
            throws Exception {
        Anuncio anuncio = combinado();
        UUID prestadorId = prestadorDe(anuncio);
        Reagendamento proposta = vencida(anuncio);

        rodarEmParalelo(job::executar, () -> iniciarServico.executar(anuncio.getId(), prestadorId));

        // o serviço sempre inicia; a proposta termina ou expirada (job primeiro) ou sem efeito (início primeiro)
        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.EM_ANDAMENTO);
        StatusReagendamento finalStatus = recarregar(proposta).getStatus();
        assertThat(finalStatus).isIn(StatusReagendamento.EXPIRADO, StatusReagendamento.SEM_EFEITO);
        long esperados = finalStatus == StatusReagendamento.EXPIRADO ? 1 : 0;
        assertThat(auditorias()).isEqualTo(esperados);
        assertThat(eventos.stream(ReagendamentoExpiradoEvent.class).count()).isEqualTo(esperados);
    }

    private static void rodarEmParalelo(Runnable... tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.length);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<?>> futuros = Arrays.stream(tarefas)
                    .map(tarefa -> pool.submit(() -> {
                        largada.await();
                        tarefa.run();
                        return null;
                    }))
                    .collect(Collectors.toList());
            largada.countDown();
            for (Future<?> futuro : futuros) {
                futuro.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
