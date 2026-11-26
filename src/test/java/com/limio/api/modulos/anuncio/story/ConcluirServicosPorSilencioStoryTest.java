package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.eventos.ServicoConcluidoEvent;
import com.limio.api.comum.eventos.enums.OrigemConclusao;
import com.limio.api.comum.portas.ConsultaServico;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.ConcluirServicosPorSilencioUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.ExpirarReagendamentosUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoRepository;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

/**
 * BDD do job de conclusão por silêncio (ETI-98), contra Postgres real. O job usa o relógio real, então os serviços
 * entram com datas no passado. Sem HTTP: a story chama {@code executar()} direto (o cron fica desligado nos testes).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class ConcluirServicosPorSilencioStoryTest {

    private static final Duration UMA_HORA = Duration.ofHours(1);
    private static final Duration UM_DIA = Duration.ofDays(1);

    @Autowired
    private ConcluirServicosPorSilencioUseCase job;

    @Autowired
    private ExpirarReagendamentosUseCase jobDeExpirarPropostas;

    @Autowired
    private ConsultaServico consultaServico;

    @MockitoSpyBean
    private AnuncioService anuncioService;

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

    @Value("${anuncio.conclusao-silencio.lote}")
    private int lote;

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

    private static Instant ha(Duration tempo) {
        return Instant.now().minus(tempo).truncatedTo(ChronoUnit.MICROS);
    }

    private Anuncio.AnuncioBuilder anuncio(StatusAnuncio status, Instant dataHoraPretendida) {
        return Anuncio.builder()
                .contratanteId(maria.getId())
                .titulo("Instalar 3 tomadas")
                .categoriaId(UUID.randomUUID()) // sem FK pra categoria (ADR-0001 §1.6)
                .descricao("Instalar 3 tomadas novas na sala e trocar o disjuntor")
                .dataHoraPretendida(dataHoraPretendida)
                .bairroRegiaoSnapshot("Pinheiros")
                .prazoValidacaoHoras(24)
                .status(status);
    }

    private Anuncio comEscolhido(Anuncio.AnuncioBuilder construtor) {
        Anuncio anuncio = anuncioRepository.save(construtor.build());
        candidatura(anuncio, anuncio.getStatus() == StatusAnuncio.AGUARDANDO_VALIDACAO
                ? StatusAplicacao.AGUARDANDO_VALIDACAO : StatusAplicacao.ESCOLHIDA);
        return anuncio;
    }

    private void candidatura(Anuncio anuncio, StatusAplicacao status) {
        aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(carlos.getId())
                .status(status)
                .dataAplicacao(ha(UM_DIA.multipliedBy(10)))
                .escolhidaEm(status == StatusAplicacao.ENVIADA ? null : ha(UM_DIA.multipliedBy(9)))
                .build());
    }

    /** Combinado cuja data vigente passou há 25h: já elegível. */
    private Anuncio vencido() {
        return comEscolhido(anuncio(StatusAnuncio.PUBLICADO, ha(Duration.ofHours(25))));
    }

    private Anuncio comCheckoutHa(Duration tempo) {
        return comEscolhido(anuncio(StatusAnuncio.AGUARDANDO_VALIDACAO, ha(UM_DIA.multipliedBy(5)))
                .checkoutEm(ha(tempo)));
    }

    private Reagendamento proposta(Anuncio anuncio, StatusReagendamento status, Instant expiraEm) {
        return reagendamentoRepository.save(Reagendamento.builder()
                .anuncioId(anuncio.getId())
                .dataAnterior(anuncio.getDataHoraPretendida())
                .dataProposta(Instant.now().plus(UM_DIA).truncatedTo(ChronoUnit.MICROS))
                .justificativa("Imprevisto de agenda")
                .status(status)
                .proponenteId(maria.getId())
                .expiraEm(expiraEm.truncatedTo(ChronoUnit.MICROS))
                .build());
    }

    private Anuncio recarregar(Anuncio anuncio) {
        return anuncioRepository.findById(anuncio.getId()).orElseThrow();
    }

    private long auditorias() {
        return jdbc.queryForObject(
                "select count(*) from log_auditoria where tipo_acao = 'SERVICO_CONCLUIDO_POR_SILENCIO'", Long.class);
    }

    private List<UUID> avisados() {
        return jdbc.queryForList("select usuario_id from notificacao where tipo = 'SERVICO_CONCLUIDO'", UUID.class);
    }

    private void assertConcluido(Anuncio anuncio) {
        Anuncio atual = recarregar(anuncio);
        assertThat(atual.getStatus()).isEqualTo(StatusAnuncio.CONCLUIDO);
        assertThat(atual.getValidadoEm()).isNotNull();
        assertThat(atual.getEncerradoEm()).isEqualTo(atual.getValidadoEm());
    }

    private void assertIntocado(Anuncio anuncio) {
        Anuncio atual = recarregar(anuncio);
        assertThat(atual.getStatus()).isEqualTo(anuncio.getStatus());
        assertThat(atual.getValidadoEm()).isNull();
        assertThat(eventos.stream(ServicoConcluidoEvent.class)).isEmpty();
        assertThat(auditorias()).isZero();
        assertThat(avisados()).isEmpty();
    }

    // ---------------------------------------------------------------- conclui

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO" })
    void dadaDataVigenteVencidaHaMaisDe24h_quandoRodaOJob_entaoConcluiAvisaAsDuasPartesELiberaAAvaliacao(
            StatusAnuncio status) {
        Anuncio anuncio = comEscolhido(anuncio(status, ha(Duration.ofHours(25))));

        job.executar();

        assertConcluido(anuncio);
        List<ServicoConcluidoEvent> publicados = eventos.stream(ServicoConcluidoEvent.class).toList();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.get(0).origem()).isEqualTo(OrigemConclusao.SILENCIO);
        assertThat(publicados.get(0).prestadorId()).isEqualTo(carlos.getId());
        assertThat(avisados()).containsExactlyInAnyOrder(maria.getId(), carlos.getId());
        assertThat(auditorias()).isEqualTo(1);
        assertThat(consultaServico.buscarPorId(anuncio.getId()).orElseThrow().concluidoEm())
                .isEqualTo(recarregar(anuncio).getValidadoEm());
    }

    @Test
    void dadoCheckoutSemValidacaoComPrazoVencido_quandoRodaOJob_entaoConclui() {
        Anuncio anuncio = comCheckoutHa(Duration.ofHours(25));

        job.executar();

        assertConcluido(anuncio);
    }

    @Test
    void dadoAnuncioSemPrazoDeValidacao_quandoRodaOJob_entaoUsaOPadrao() {
        Anuncio anuncio = comEscolhido(anuncio(StatusAnuncio.AGUARDANDO_VALIDACAO, ha(UM_DIA.multipliedBy(5)))
                .prazoValidacaoHoras(null)
                .checkoutEm(ha(Duration.ofHours(25))));

        job.executar();

        assertConcluido(anuncio);
    }

    // ---------------------------------------------------------------- não conclui

    @Test
    void dadaDataVigenteAindaDentroDoPrazo_quandoRodaOJob_entaoNaoConclui() {
        Anuncio anuncio = comEscolhido(anuncio(StatusAnuncio.PUBLICADO, ha(Duration.ofHours(23))));

        job.executar();

        assertIntocado(anuncio);
    }

    @Test
    void dadoCheckoutAindaDentroDoPrazo_quandoRodaOJob_entaoNaoConclui() {
        Anuncio anuncio = comCheckoutHa(Duration.ofHours(23));

        job.executar();

        assertIntocado(anuncio);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "CONCLUIDO", "ENCERRADO" })
    void dadoEstadoFinal_quandoRodaOJob_entaoNaoReprocessa(StatusAnuncio status) {
        Anuncio anuncio = comEscolhido(anuncio(status, ha(UM_DIA.multipliedBy(5))));

        job.executar();

        assertIntocado(anuncio);
    }

    @Test
    void dadoAnuncioAguardandoCandidatos_quandoRodaOJob_entaoNaoProcessa() {
        Anuncio anuncio = anuncioRepository.save(anuncio(StatusAnuncio.PUBLICADO, ha(UM_DIA.multipliedBy(5))).build());
        candidatura(anuncio, StatusAplicacao.ENVIADA);

        job.executar();

        assertIntocado(anuncio);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "AGUARDANDO_VALIDACAO" })
    void dadaContaEncerradaComAnuncioSobrandoAberto_quandoRodaOJob_entaoNaoConcluiPorSilencio(StatusAnuncio status) {
        Anuncio anuncio = comEscolhido(anuncio(status, ha(UM_DIA.multipliedBy(5))).checkoutEm(
                status == StatusAnuncio.AGUARDANDO_VALIDACAO ? ha(UM_DIA.multipliedBy(4)) : null));
        carlos.setAnonimizadoEm(Instant.now());
        usuarioRepository.save(carlos);

        job.executar();

        assertIntocado(anuncio);
    }

    // ---------------------------------------------------------------- reagendamento

    @Test
    void dadaPropostaPendenteMesmoJaVencida_quandoRodaOJob_entaoNaoConclui() {
        Anuncio anuncio = vencido();
        proposta(anuncio, StatusReagendamento.PENDENTE, ha(UMA_HORA));

        job.executar();

        assertIntocado(anuncio);
    }

    @Test
    void dadaPropostaQueExpira_quandoOJobRodaDeNovo_entaoConclui() {
        Anuncio anuncio = vencido();
        proposta(anuncio, StatusReagendamento.PENDENTE, ha(UMA_HORA));
        job.executar();
        assertIntocado(anuncio);

        jobDeExpirarPropostas.executar();
        job.executar();

        assertConcluido(anuncio);
    }

    @ParameterizedTest
    @EnumSource(value = StatusReagendamento.class, names = { "RECUSADO", "EXPIRADO", "CANCELADO" })
    void dadaPropostaEncerradaSemAceiteEDataOriginalVencida_quandoRodaOJob_entaoConclui(StatusReagendamento status) {
        Anuncio anuncio = vencido();
        proposta(anuncio, status, ha(UMA_HORA));

        job.executar();

        assertConcluido(anuncio);
    }

    @Test
    void dadoReagendamentoAceitoComNovaDataNaoVencida_quandoRodaOJob_entaoNaoConclui() {
        Anuncio anuncio = comEscolhido(anuncio(StatusAnuncio.PUBLICADO, ha(UMA_HORA)));
        proposta(anuncio, StatusReagendamento.ACEITO, ha(UMA_HORA));

        job.executar();

        assertIntocado(anuncio);
    }

    @Test
    void dadoReagendamentoAceitoComNovaDataVencidaAlemDoPrazo_quandoRodaOJob_entaoConclui() {
        Anuncio anuncio = vencido();
        proposta(anuncio, StatusReagendamento.ACEITO, ha(UM_DIA.multipliedBy(3)));

        job.executar();

        assertConcluido(anuncio);
    }

    // ---------------------------------------------------------------- idempotência, lote e falha

    @Test
    void dadoJobRodadoDuasVezes_quandoRodaDeNovo_entaoNaoDuplicaEventoAuditoriaNemAviso() {
        Anuncio anuncio = vencido();

        job.executar();
        Instant concluidoNaPrimeira = recarregar(anuncio).getValidadoEm();
        job.executar();

        assertThat(recarregar(anuncio).getValidadoEm()).isEqualTo(concluidoNaPrimeira);
        assertThat(eventos.stream(ServicoConcluidoEvent.class)).hasSize(1);
        assertThat(auditorias()).isEqualTo(1);
        assertThat(avisados()).hasSize(2);
    }

    @Test
    void dadoUmLoteInteiroDeAnunciosSemEscolhidoMaisAntigos_quandoRodaOJob_entaoOElegivelNaoFicaDeFora() {
        for (int i = 0; i < lote; i++) {
            anuncioRepository.save(anuncio(StatusAnuncio.PUBLICADO, ha(UM_DIA.multipliedBy(30))).build());
        }
        Anuncio elegivel = vencido();

        job.executar();

        assertConcluido(elegivel);
    }

    @Test
    void dadoUmLoteInteiroDeContaEncerradaMaisAntigo_quandoRodaOJob_entaoOElegivelNaoFicaDeFora() {
        Usuario encerrada = usuario("Ana Lima", "ana@example.com", "39053344705", PapelUsuario.EMPREGADOR);
        encerrada.setAnonimizadoEm(Instant.now());
        usuarioRepository.save(encerrada);
        for (int i = 0; i < lote; i++) {
            comEscolhido(anuncio(StatusAnuncio.PUBLICADO, ha(UM_DIA.multipliedBy(30)))
                    .contratanteId(encerrada.getId()));
        }
        Anuncio elegivel = vencido();

        job.executar();

        assertConcluido(elegivel);
        assertThat(eventos.stream(ServicoConcluidoEvent.class)).hasSize(1);
    }

    @Test
    void dadaFalhaAoGravar_quandoRodaOJob_entaoNadaMudaENadaEPublicado() {
        Anuncio anuncio = vencido();
        doThrow(new IllegalStateException("falha simulada")).when(anuncioService).salvar(any());

        job.executar(); // a falha é logada, não propaga

        assertIntocado(anuncio);
    }
}
