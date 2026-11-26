package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.ServicoConcluidoEvent;
import com.limio.api.comum.eventos.enums.OrigemConclusao;
import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.ImagensDeTeste;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.actions.usecase.CancelarServicoPeloPrestadorUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.ConcluirServicosPorSilencioUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.ProporReagendamentoUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.RealizarCheckoutUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.ResponderReagendamentoUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.ValidarServicoUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.DecisaoReagendamento;
import com.limio.api.modulos.anuncio.enums.ModoValidacao;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoRepository;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

/**
 * Concorrência da conclusão por silêncio (ETI-98), contra Postgres real e com threads reais. Para a ordem não ficar
 * por conta do escalonador, o teste segura a trava {@code ANUNCIO}, enfileira os concorrentes um a um (o Postgres
 * atende a fila na ordem de chegada) e só então a solta. Assim o job sempre pré-seleciona o anúncio antes de o outro
 * lado agir, e o que se testa é a reconferência sob a trava. Se um dos lados deixar de pegar a trava, o teste falha.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class ConclusaoPorSilencioConcorrenciaStoryTest {

    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(10);

    @Autowired
    private ConcluirServicosPorSilencioUseCase job;

    @Autowired
    private ValidarServicoUseCase validar;

    @Autowired
    private RealizarCheckoutUseCase checkout;

    @Autowired
    private CancelarServicoPeloPrestadorUseCase cancelarPeloPrestador;

    @Autowired
    private ProporReagendamentoUseCase propor;

    @Autowired
    private ResponderReagendamentoUseCase responder;

    @MockitoBean
    private ArmazenamentoService armazenamentoService;

    @Autowired
    private TravaTransacional trava;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    @Autowired
    private ReagendamentoRepository reagendamentoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents eventos;

    private Usuario maria;
    private Usuario carlos;

    @BeforeEach
    void prepararBase() {
        limparBase();
        maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        carlos = usuario("Carlos Souza", "carlos@example.com", "11144477735", PapelUsuario.PRESTADOR);
    }

    @AfterEach
    void limparBase() {
        jdbc.execute("delete from envio_notificacao");
        jdbc.execute("delete from notificacao");
        jdbc.execute("delete from log_auditoria");
        jdbc.execute("delete from midia");
        reagendamentoRepository.deleteAll();
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // ---------------------------------------------------------------- cenário

    private Usuario usuario(String nome, String email, String cpf, PapelUsuario papel) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nome)
                .email(email)
                .telefone("11988887777")
                .senhaHash("hash-irrelevante")
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(30))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(papel)
                .statusConta(StatusConta.ATIVA)
                .build());
    }

    /** Combinado cuja data vigente passou há 25h: elegível, e ainda aceita check-out, validação e proposta. */
    private Anuncio vencido() {
        Anuncio anuncio = anuncioRepository.save(Anuncio.builder()
                .contratanteId(maria.getId())
                .titulo("Instalar 3 tomadas")
                .categoriaId(UUID.randomUUID()) // sem FK pra categoria (ADR-0001 §1.6)
                .descricao("Instalar 3 tomadas novas na sala e trocar o disjuntor")
                .dataHoraPretendida(Instant.now().minus(Duration.ofHours(25)).truncatedTo(ChronoUnit.MICROS))
                .bairroRegiaoSnapshot("Pinheiros")
                .prazoValidacaoHoras(24)
                .status(StatusAnuncio.PUBLICADO)
                .build());
        aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(carlos.getId())
                .status(StatusAplicacao.ESCOLHIDA)
                .dataAplicacao(Instant.now().minus(Duration.ofDays(10)))
                .escolhidaEm(Instant.now().minus(Duration.ofDays(9)))
                .build());
        return anuncio;
    }

    private void proporNovaData(Anuncio anuncio) {
        propor.executar(maria.getId(), anuncio.getId(), Instant.now().plus(Duration.ofDays(2)), "Outra data", null);
    }

    private void fazerCheckout(Anuncio anuncio) {
        checkout.executar(anuncio.getId(), carlos.getId(), List.of(ImagensDeTeste.jpeg(40, 30)), null);
    }

    // ---------------------------------------------------------------- corrida

    private record Resultado(Throwable primeiro, Throwable segundo) {
    }

    /** Enfileira as duas tarefas na trava do anúncio, nesta ordem, solta e devolve o que cada uma lançou. */
    private Resultado enfileirar(Anuncio anuncio, Runnable primeiro, Runnable segundo) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<Throwable>> futuros = transacao.execute(status -> {
                trava.travar(EscopoTrava.ANUNCIO, anuncio.getId().toString());
                CompletableFuture<Throwable> a = rodar(pool, primeiro);
                aguardarNaFila(1);
                CompletableFuture<Throwable> b = rodar(pool, segundo);
                aguardarNaFila(2);
                return List.of(a, b);
            });
            return new Resultado(resultadoDe(futuros.get(0)), resultadoDe(futuros.get(1)));
        } finally {
            pool.shutdownNow();
        }
    }

    private static CompletableFuture<Throwable> rodar(ExecutorService pool, Runnable tarefa) {
        return CompletableFuture.runAsync(tarefa, pool)
                .handle((nada, erro) -> erro == null ? null : erro.getCause());
    }

    private static Throwable resultadoDe(CompletableFuture<Throwable> futuro) {
        return futuro.orTimeout(30, TimeUnit.SECONDS).join();
    }

    private void aguardarNaFila(int esperados) {
        Instant limite = Instant.now().plus(ESPERA_MAXIMA);
        while (jdbc.queryForObject("select count(*) from pg_locks where locktype = 'advisory' and not granted",
                Integer.class) < esperados) {
            assertThat(Instant.now()).as("%s tarefa(s) esperando a trava do anúncio", esperados).isBefore(limite);
            Thread.onSpinWait();
        }
    }

    // ---------------------------------------------------------------- invariantes

    private Anuncio recarregar(Anuncio anuncio) {
        return anuncioRepository.findById(anuncio.getId()).orElseThrow();
    }

    private List<Reagendamento> propostas() {
        return reagendamentoRepository.findAll();
    }

    private long auditoriasDeSilencio() {
        return jdbc.queryForObject(
                "select count(*) from log_auditoria where tipo_acao = 'SERVICO_CONCLUIDO_POR_SILENCIO'", Long.class);
    }

    private long avisosDeConclusao() {
        return jdbc.queryForObject("select count(*) from notificacao where tipo = 'SERVICO_CONCLUIDO'", Long.class);
    }

    /** Concluído uma única vez, pela origem informada: um evento, um aviso por parte e auditoria só no silêncio. */
    private void assertConcluidoUmaVez(Anuncio anuncio, OrigemConclusao origem) {
        assertThat(recarregar(anuncio).getStatus()).isEqualTo(StatusAnuncio.CONCLUIDO);
        assertThat(eventos.stream(ServicoConcluidoEvent.class)).extracting(ServicoConcluidoEvent::origem)
                .containsExactly(origem);
        assertThat(avisosDeConclusao()).isEqualTo(2);
        assertThat(auditoriasDeSilencio()).isEqualTo(origem == OrigemConclusao.SILENCIO ? 1 : 0);
        assertThat(propostas()).noneMatch(proposta -> proposta.getStatus() == StatusReagendamento.PENDENTE);
    }

    private void assertNaoConcluido(Anuncio anuncio, StatusAnuncio esperado) {
        Anuncio atual = recarregar(anuncio);
        assertThat(atual.getStatus()).isEqualTo(esperado);
        assertThat(atual.getValidadoEm()).isNull();
        assertThat(eventos.stream(ServicoConcluidoEvent.class)).isEmpty();
        assertThat(avisosDeConclusao()).isZero();
        assertThat(auditoriasDeSilencio()).isZero();
    }

    // ---------------------------------------------------------------- corridas

    @Test
    void dadasDuasExecucoesDoJobAoMesmoTempo_quandoDisputam_entaoSoUmaConclui() {
        Anuncio anuncio = vencido();

        Resultado resultado = enfileirar(anuncio, job::executar, job::executar);

        assertThat(resultado).isEqualTo(new Resultado(null, null));
        assertConcluidoUmaVez(anuncio, OrigemConclusao.SILENCIO);
    }

    @ParameterizedTest(name = "job primeiro: {0}")
    @ValueSource(booleans = { true, false })
    void dadosJobEValidacao_quandoDisputam_entaoConcluiUmaVezPeloPrimeiro(boolean jobPrimeiro) {
        Anuncio anuncio = vencido();
        Runnable validacao = () -> validar.executar(anuncio.getId(), maria.getId(), ModoValidacao.PRESENCIAL);

        if (jobPrimeiro) {
            Resultado resultado = enfileirar(anuncio, job::executar, validacao);

            assertThat(resultado.segundo()).isInstanceOf(ServicoEstadoInvalidoException.class);
            assertConcluidoUmaVez(anuncio, OrigemConclusao.SILENCIO);
        } else {
            Resultado resultado = enfileirar(anuncio, validacao, job::executar);

            assertThat(resultado).isEqualTo(new Resultado(null, null));
            assertConcluidoUmaVez(anuncio, OrigemConclusao.VALIDACAO);
        }
    }

    @ParameterizedTest(name = "job primeiro: {0}")
    @ValueSource(booleans = { true, false })
    void dadosJobECheckout_quandoDisputam_entaoOCheckoutQueChegaAntesReiniciaOPrazo(boolean jobPrimeiro) {
        Anuncio anuncio = vencido();

        if (jobPrimeiro) {
            Resultado resultado = enfileirar(anuncio, job::executar, () -> fazerCheckout(anuncio));

            assertThat(resultado.segundo()).isInstanceOf(ServicoEstadoInvalidoException.class);
            assertConcluidoUmaVez(anuncio, OrigemConclusao.SILENCIO);
            assertThat(recarregar(anuncio).getCheckoutEm()).isNull();
        } else {
            Resultado resultado = enfileirar(anuncio, () -> fazerCheckout(anuncio), job::executar);

            assertThat(resultado).isEqualTo(new Resultado(null, null));
            assertNaoConcluido(anuncio, StatusAnuncio.AGUARDANDO_VALIDACAO);
        }
    }

    /** Só o Prestador: com a data vigente vencida, o cancelamento do Empregador já é recusado pelo prazo. */
    @ParameterizedTest(name = "job primeiro: {0}")
    @ValueSource(booleans = { true, false })
    void dadosJobECancelamento_quandoDisputam_entaoOServicoTerminaConcluidoOuEncerrado(boolean jobPrimeiro) {
        Anuncio anuncio = vencido();
        Runnable cancelamento = () -> cancelarPeloPrestador.executar(carlos.getId(), anuncio.getId());

        if (jobPrimeiro) {
            Resultado resultado = enfileirar(anuncio, job::executar, cancelamento);

            assertThat(resultado.segundo()).isInstanceOf(ServicoEstadoInvalidoException.class);
            assertConcluidoUmaVez(anuncio, OrigemConclusao.SILENCIO);
        } else {
            Resultado resultado = enfileirar(anuncio, cancelamento, job::executar);

            assertThat(resultado).isEqualTo(new Resultado(null, null));
            assertNaoConcluido(anuncio, StatusAnuncio.ENCERRADO);
        }
    }

    @ParameterizedTest(name = "job primeiro: {0}")
    @ValueSource(booleans = { true, false })
    void dadosJobEProposta_quandoDisputam_entaoNuncaFicaConcluidoComPropostaPendente(boolean jobPrimeiro) {
        Anuncio anuncio = vencido();

        if (jobPrimeiro) {
            Resultado resultado = enfileirar(anuncio, job::executar, () -> proporNovaData(anuncio));

            assertThat(resultado.segundo()).isInstanceOf(ServicoEstadoInvalidoException.class);
            assertConcluidoUmaVez(anuncio, OrigemConclusao.SILENCIO);
            assertThat(propostas()).isEmpty();
        } else {
            Resultado resultado = enfileirar(anuncio, () -> proporNovaData(anuncio), job::executar);

            assertThat(resultado).isEqualTo(new Resultado(null, null));
            assertNaoConcluido(anuncio, StatusAnuncio.PUBLICADO);
            assertThat(propostas()).extracting(Reagendamento::getStatus)
                    .containsExactly(StatusReagendamento.PENDENTE);
        }
    }

    /**
     * Aqui não há ordem a forçar: enquanto a proposta está pendente o job nem seleciona o anúncio, e depois do aceite a
     * data é futura. Em qualquer escalonamento o serviço não pode ser concluído.
     */
    @RepeatedTest(5)
    void dadosJobEAceiteSimultaneos_entaoOServicoNuncaEConcluido() {
        Anuncio anuncio = vencido();
        proporNovaData(anuncio);
        Reagendamento proposta = propostas().get(0);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Throwable> execucao = rodar(pool, job::executar);
            CompletableFuture<Throwable> aceite = rodar(pool, () -> responder.executar(carlos.getId(),
                    proposta.getId(), DecisaoReagendamento.ACEITAR, null));

            assertThat(resultadoDe(execucao)).isNull();
            assertThat(resultadoDe(aceite)).isNull();
        } finally {
            pool.shutdownNow();
        }

        assertNaoConcluido(anuncio, StatusAnuncio.PUBLICADO);
        assertThat(propostas()).extracting(Reagendamento::getStatus).containsExactly(StatusReagendamento.ACEITO);
        assertThat(recarregar(anuncio).getDataHoraPretendida()).isAfter(Instant.now());
    }
}
