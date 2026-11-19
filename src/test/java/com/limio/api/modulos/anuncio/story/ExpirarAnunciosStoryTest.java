package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.AnuncioCanceladoEvent;
import com.limio.api.comum.eventos.AnuncioExpiradoEvent;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.CancelarAnuncioUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.ExpirarAnunciosSemCandidatosUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

/**
 * BDD do job de expiração (ETI-66, RF30/RB09/UC22) com banco real. Não há endpoint: a story chama o job direto (o
 * cron fica desligado nos testes — {@code anuncio.expiracao.cron=-}). Anúncios e candidaturas nascem pelos
 * repositories, como nas outras stories do módulo.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class ExpirarAnunciosStoryTest {

    private static final Duration UM_DIA = Duration.ofDays(1);

    @Autowired
    private ExpirarAnunciosSemCandidatosUseCase job;

    @Autowired
    private CancelarAnuncioUseCase cancelarAnuncio;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    @MockitoSpyBean
    private AnuncioService anuncioService;

    @Autowired
    private ApplicationEvents eventos;

    @BeforeEach
    void limparBase() {
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private Anuncio anuncio(StatusAnuncio status, Instant dataHoraPretendida) {
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(UUID.randomUUID()) // sem FK pra usuário (ADR-0001 §1.6)
                .titulo("Instalar 3 tomadas")
                .categoriaId(UUID.randomUUID())
                .descricao("Instalar 3 tomadas novas na sala e trocar o disjuntor")
                .dataHoraPretendida(dataHoraPretendida)
                .bairroRegiaoSnapshot("Pinheiros")
                .status(status)
                .build());
    }

    private Anuncio vencido() {
        return anuncio(StatusAnuncio.PUBLICADO, Instant.now().minus(UM_DIA));
    }

    private Aplicacao aplicacao(UUID anuncioId, StatusAplicacao status) {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(UUID.randomUUID())
                .status(status)
                .dataAplicacao(Instant.now().minus(UM_DIA.multipliedBy(2)))
                .build();
        if (status == StatusAplicacao.ESCOLHIDA || status == StatusAplicacao.AGUARDANDO_VALIDACAO) {
            aplicacao.setEscolhidaEm(Instant.now().minus(UM_DIA.multipliedBy(2)));
        }
        return aplicacaoRepository.save(aplicacao);
    }

    private Anuncio recarregar(Anuncio anuncio) {
        return anuncioRepository.findById(anuncio.getId()).orElseThrow();
    }

    private void assertNaoMudou(Anuncio anuncio, StatusAnuncio statusEsperado) {
        Anuncio atual = recarregar(anuncio);
        assertThat(atual.getStatus()).isEqualTo(statusEsperado);
        assertThat(atual.getEncerradoEm()).isNull();
        assertThat(atual.getMotivoEncerramento()).isNull();
    }

    @Test
    void dadoAnuncioVencidoSemCandidaturas_quandoRodaOJob_entaoExpiraEPublicaUmEvento() {
        Anuncio anuncio = vencido();

        job.executar();

        Anuncio atual = recarregar(anuncio);
        assertThat(atual.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(atual.getEncerradoEm()).isNotNull();
        assertThat(atual.getMotivoEncerramento()).isEqualTo("Expirado sem candidatos");
        List<AnuncioExpiradoEvent> publicados = eventos.stream(AnuncioExpiradoEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).anuncioId()).isEqualTo(anuncio.getId());
        assertThat(publicados.get(0).contratanteId()).isEqualTo(anuncio.getContratanteId());
        assertThat(publicados.get(0).titulo()).isEqualTo("Instalar 3 tomadas");
    }

    @Test
    void dadoAnuncioVencidoComCandidaturaEmQualquerEstado_quandoRodaOJob_entaoNaoExpira() {
        for (StatusAplicacao status : StatusAplicacao.values()) {
            Anuncio anuncio = vencido();
            aplicacao(anuncio.getId(), status);

            job.executar();

            assertNaoMudou(anuncio, StatusAnuncio.PUBLICADO);
        }
        assertThat(eventos.stream(AnuncioExpiradoEvent.class)).isEmpty();
    }

    @Test
    void dadoAnuncioComDataNoFuturo_quandoRodaOJob_entaoNaoExpira() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, Instant.now().plus(UM_DIA));

        job.executar();

        assertNaoMudou(anuncio, StatusAnuncio.PUBLICADO);
        assertThat(eventos.stream(AnuncioExpiradoEvent.class)).isEmpty();
    }

    @Test
    void dadoAnuncioForaDePublicado_quandoRodaOJob_entaoNaoExpiraNemReprocessa() {
        Anuncio emAndamento = anuncio(StatusAnuncio.EM_ANDAMENTO, Instant.now().minus(UM_DIA));
        Anuncio aguardando = anuncio(StatusAnuncio.AGUARDANDO_VALIDACAO, Instant.now().minus(UM_DIA));
        Anuncio concluido = anuncio(StatusAnuncio.CONCLUIDO, Instant.now().minus(UM_DIA));
        Anuncio encerrado = anuncio(StatusAnuncio.ENCERRADO, Instant.now().minus(UM_DIA));
        Instant encerradoEm = Instant.now().minus(UM_DIA).truncatedTo(ChronoUnit.MICROS); // precisão do Postgres
        encerrado.setEncerradoEm(encerradoEm);
        encerrado.setMotivoEncerramento("Cancelado pelo Empregador");
        anuncioRepository.save(encerrado);

        job.executar();

        assertNaoMudou(emAndamento, StatusAnuncio.EM_ANDAMENTO);
        assertNaoMudou(aguardando, StatusAnuncio.AGUARDANDO_VALIDACAO);
        assertNaoMudou(concluido, StatusAnuncio.CONCLUIDO);
        Anuncio encerradoAtual = recarregar(encerrado);
        assertThat(encerradoAtual.getMotivoEncerramento()).isEqualTo("Cancelado pelo Empregador");
        assertThat(encerradoAtual.getEncerradoEm()).isEqualTo(encerradoEm);
        assertThat(eventos.stream(AnuncioExpiradoEvent.class)).isEmpty();
    }

    @Test
    void dadoJobRodadoDuasVezes_quandoRodaDeNovo_entaoNaoEncerraNemPublicaOutraVez() {
        Anuncio anuncio = vencido();

        job.executar();
        Instant encerradoNaPrimeira = recarregar(anuncio).getEncerradoEm();
        job.executar();

        assertThat(recarregar(anuncio).getEncerradoEm()).isEqualTo(encerradoNaPrimeira);
        assertThat(eventos.stream(AnuncioExpiradoEvent.class)).hasSize(1);
    }

    @Test
    void dadoVariosElegiveisEUmComCandidatura_quandoRodaOJob_entaoExpiraSoOsElegiveis() {
        Anuncio a = vencido();
        Anuncio b = vencido();
        Anuncio c = vencido();
        Anuncio comCandidatura = vencido();
        aplicacao(comCandidatura.getId(), StatusAplicacao.ENVIADA);

        job.executar();

        assertThat(recarregar(a).getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(recarregar(b).getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(recarregar(c).getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertNaoMudou(comCandidatura, StatusAnuncio.PUBLICADO);
        assertThat(eventos.stream(AnuncioExpiradoEvent.class)).hasSize(3);
    }

    @Test
    void dadaFalhaAoGravar_quandoRodaOJob_entaoRollbackSemEventoEAnuncioContinuaPublicado() {
        Anuncio anuncio = vencido();
        doThrow(new IllegalStateException("falha simulada")).when(anuncioService).salvar(any());

        job.executar(); // a falha é logada, não propaga

        assertNaoMudou(anuncio, StatusAnuncio.PUBLICADO);
        assertThat(eventos.stream(AnuncioExpiradoEvent.class)).isEmpty();
    }

    @Test
    void dadoJobEmUmaInstanciaEOutraTambem_quandoRodamAoMesmoTempo_entaoUmUnicoEncerramentoEUmEvento()
            throws Exception {
        Anuncio anuncio = vencido();

        rodarEmParalelo(job::executar, job::executar);

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(eventos.stream(AnuncioExpiradoEvent.class)).hasSize(1);
    }

    @Test
    void dadoJobECancelamentoAoMesmoTempo_quandoDisputam_entaoSoUmVence() throws Exception {
        Anuncio anuncio = vencido();

        rodarEmParalelo(job::executar, () -> {
            try {
                cancelarAnuncio.executar(anuncio.getId(), anuncio.getContratanteId());
            } catch (RuntimeException e) {
                // quem perde a corrida vê o anúncio já encerrado: exceção de estado inválido, esperada
            }
        });

        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        long expirados = eventos.stream(AnuncioExpiradoEvent.class).count();
        long cancelados = eventos.stream(AnuncioCanceladoEvent.class).count();
        assertThat(expirados + cancelados).isEqualTo(1);
    }

    private static void rodarEmParalelo(Runnable... tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.length);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<?>> futuros = java.util.Arrays.stream(tarefas)
                    .map(tarefa -> pool.submit(() -> {
                        largada.await();
                        tarefa.run();
                        return null;
                    }))
                    .collect(java.util.stream.Collectors.toList());
            largada.countDown();
            for (Future<?> futuro : futuros) {
                futuro.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
