package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.CriterioDoHistorico;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.CausaEncerramento;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.enums.StatusHistorico;

/**
 * BDD das consultas do histórico (ETI-71) com banco real: as duas queries paginadas de anúncio (Empregador e
 * Prestador) e os finders em lote de candidatura. Sem endpoint ainda: chama os services direto, como
 * {@code ExpirarAnunciosStoryTest}. A story HTTP ponta a ponta vem com o endpoint.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ConsultasDoHistoricoStoryTest {

    private static final Instant BASE = Instant.parse("2026-10-10T12:00:00Z");
    private static final CriterioDoHistorico SEM_FILTRO = new CriterioDoHistorico(null, null, null);

    @Autowired
    private AnuncioService anuncioService;

    @Autowired
    private AplicacaoService aplicacaoService;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    private final UUID empregador = UUID.randomUUID();
    private final UUID outroEmpregador = UUID.randomUUID();
    private final UUID prestador = UUID.randomUUID();
    private final UUID outroPrestador = UUID.randomUUID();

    @BeforeEach
    void limparBase() {
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private Anuncio anuncio(UUID contratante, String titulo, StatusAnuncio status, CausaEncerramento causa,
            Instant validadoEm, Instant encerradoEm) {
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(contratante) // sem FK pra usuário (ADR-0001 §1.6)
                .titulo(titulo)
                .categoriaId(UUID.randomUUID())
                .descricao("Descrição de " + titulo)
                .dataHoraPretendida(BASE.minus(Duration.ofDays(30)))
                .status(status)
                .causaEncerramento(causa)
                .validadoEm(validadoEm)
                .encerradoEm(encerradoEm)
                .build());
    }

    private Anuncio concluido(UUID contratante, String titulo, Instant validadoEm) {
        return anuncio(contratante, titulo, StatusAnuncio.CONCLUIDO, null, validadoEm, null);
    }

    private Anuncio cancelado(UUID contratante, String titulo, Instant encerradoEm) {
        return anuncio(contratante, titulo, StatusAnuncio.ENCERRADO, CausaEncerramento.CANCELAMENTO, null,
                encerradoEm);
    }

    private Anuncio expirado(UUID contratante, String titulo, Instant encerradoEm) {
        return anuncio(contratante, titulo, StatusAnuncio.ENCERRADO, CausaEncerramento.EXPIRACAO, null, encerradoEm);
    }

    private Aplicacao aplicacao(Anuncio anuncio, UUID prestadorId, StatusAplicacao status, boolean escolhida) {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(prestadorId)
                .status(status)
                .dataAplicacao(BASE.minus(Duration.ofDays(40)))
                .build();
        if (escolhida) {
            aplicacao.setEscolhidaEm(BASE.minus(Duration.ofDays(35)));
        }
        return aplicacaoRepository.save(aplicacao);
    }

    private static List<String> titulos(Page<Anuncio> pagina) {
        return pagina.getContent().stream().map(Anuncio::getTitulo).toList();
    }

    // ---- histórico do Empregador

    @Test
    void dadoAnunciosEmTodosOsStatus_quandoEmpregadorConsulta_entaoSoEstadosFinaisDoPrimeiroDoMaisNovoAoMaisAntigo() {
        concluido(empregador, "concluido", BASE);
        cancelado(empregador, "cancelado", BASE.minus(Duration.ofDays(1)));
        expirado(empregador, "expirado", BASE.minus(Duration.ofDays(2)));
        anuncio(empregador, "publicado", StatusAnuncio.PUBLICADO, null, null, null);
        anuncio(empregador, "em andamento", StatusAnuncio.EM_ANDAMENTO, null, null, null);
        anuncio(empregador, "aguardando", StatusAnuncio.AGUARDANDO_VALIDACAO, null, null, null);

        Page<Anuncio> pagina = anuncioService.buscarHistoricoDoContratante(empregador, SEM_FILTRO, 0, 20);

        assertThat(titulos(pagina)).containsExactly("concluido", "cancelado", "expirado");
        assertThat(pagina.getTotalElements()).isEqualTo(3);
    }

    @Test
    void dadoAnunciosDeOutroEmpregador_quandoEmpregadorConsulta_entaoNaoAparecem() {
        concluido(empregador, "meu", BASE);
        concluido(outroEmpregador, "de outro", BASE);

        assertThat(titulos(anuncioService.buscarHistoricoDoContratante(empregador, SEM_FILTRO, 0, 20)))
                .containsExactly("meu");
    }

    @Test
    void dadoCadaStatusDoFiltro_quandoEmpregadorConsulta_entaoSoOsDaquelaCategoria() {
        concluido(empregador, "concluido", BASE);
        cancelado(empregador, "cancelado", BASE);
        expirado(empregador, "expirado", BASE);

        assertThat(titulos(anuncioService.buscarHistoricoDoContratante(empregador,
                new CriterioDoHistorico(StatusHistorico.CONCLUIDO, null, null), 0, 20))).containsExactly("concluido");
        assertThat(titulos(anuncioService.buscarHistoricoDoContratante(empregador,
                new CriterioDoHistorico(StatusHistorico.CANCELADO, null, null), 0, 20))).containsExactly("cancelado");
        assertThat(titulos(anuncioService.buscarHistoricoDoContratante(empregador,
                new CriterioDoHistorico(StatusHistorico.EXPIRADO, null, null), 0, 20))).containsExactly("expirado");
    }

    @Test
    void dadoPeriodo_quandoEmpregadorConsulta_entaoInicioInclusivoEFimExclusivoSobreADataDoDesfecho() {
        Instant inicio = Instant.parse("2026-10-01T03:00:00Z");
        Instant fim = Instant.parse("2026-11-01T03:00:00Z");
        concluido(empregador, "antes", inicio.minusSeconds(1));
        concluido(empregador, "no inicio", inicio);
        cancelado(empregador, "no meio", inicio.plus(Duration.ofDays(10)));
        concluido(empregador, "ultimo instante", fim.minusSeconds(1));
        expirado(empregador, "no fim", fim);

        Page<Anuncio> pagina = anuncioService.buscarHistoricoDoContratante(empregador,
                new CriterioDoHistorico(null, inicio, fim), 0, 20);

        assertThat(titulos(pagina)).containsExactly("ultimo instante", "no meio", "no inicio");
        assertThat(pagina.getTotalElements()).isEqualTo(3);
    }

    @Test
    void dadoSoUmaPontaDoPeriodo_quandoEmpregadorConsulta_entaoAOutraFicaSemLimite() {
        Instant corte = Instant.parse("2026-10-05T03:00:00Z");
        concluido(empregador, "velho", corte.minus(Duration.ofDays(100)));
        concluido(empregador, "novo", corte.plus(Duration.ofDays(100)));

        assertThat(titulos(anuncioService.buscarHistoricoDoContratante(empregador,
                new CriterioDoHistorico(null, corte, null), 0, 20))).containsExactly("novo");
        assertThat(titulos(anuncioService.buscarHistoricoDoContratante(empregador,
                new CriterioDoHistorico(null, null, corte), 0, 20))).containsExactly("velho");
    }

    @Test
    void dadoPeriodoSemAnuncios_quandoEmpregadorConsulta_entaoPaginaVazia() {
        concluido(empregador, "outubro", BASE);

        Page<Anuncio> pagina = anuncioService.buscarHistoricoDoContratante(empregador, new CriterioDoHistorico(null,
                Instant.parse("2025-01-01T03:00:00Z"), Instant.parse("2025-02-01T03:00:00Z")), 0, 20);

        assertThat(pagina.getContent()).isEmpty();
        assertThat(pagina.getTotalElements()).isZero();
    }

    @Test
    void dadoMaisAnunciosQueOTamanho_quandoEmpregadorPaginaUsandoOMesmoInstante_entaoOrdemEstavelSemRepetirNemPerder() {
        for (int i = 0; i < 5; i++) {
            concluido(empregador, "c" + i, BASE); // mesma data: o id desempata
        }

        Page<Anuncio> p0 = anuncioService.buscarHistoricoDoContratante(empregador, SEM_FILTRO, 0, 2);
        Page<Anuncio> p1 = anuncioService.buscarHistoricoDoContratante(empregador, SEM_FILTRO, 1, 2);
        Page<Anuncio> p2 = anuncioService.buscarHistoricoDoContratante(empregador, SEM_FILTRO, 2, 2);

        assertThat(p0.getTotalElements()).isEqualTo(5);
        assertThat(p0.getTotalPages()).isEqualTo(3);
        List<String> todos = new java.util.ArrayList<>();
        todos.addAll(titulos(p0));
        todos.addAll(titulos(p1));
        todos.addAll(titulos(p2));
        assertThat(todos).containsExactlyInAnyOrder("c0", "c1", "c2", "c3", "c4").doesNotHaveDuplicates();
        assertThat(p2.getContent()).hasSize(1);
    }

    // ---- histórico do Prestador

    @Test
    void dadoCandidaturasEmAnunciosFinais_quandoPrestadorConsulta_entaoSoOsEmQueTemCandidatura() {
        Anuncio escolhido = concluido(empregador, "fui escolhido", BASE);
        aplicacao(escolhido, prestador, StatusAplicacao.CONCLUIDA, true);
        Anuncio cancelado = cancelado(outroEmpregador, "cancelaram", BASE.minus(Duration.ofDays(1)));
        aplicacao(cancelado, prestador, StatusAplicacao.ENCERRADA, false);
        concluido(empregador, "sem candidatura minha", BASE);
        Anuncio deOutro = concluido(empregador, "so de outro prestador", BASE);
        aplicacao(deOutro, outroPrestador, StatusAplicacao.CONCLUIDA, true);

        Page<Anuncio> pagina = anuncioService.buscarHistoricoDoPrestador(prestador, SEM_FILTRO, 0, 20);

        assertThat(titulos(pagina)).containsExactly("fui escolhido", "cancelaram");
        assertThat(pagina.getTotalElements()).isEqualTo(2);
    }

    @Test
    void dadoVariasCandidaturasDoMesmoPrestadorNoAnuncio_quandoConsulta_entaoOAnuncioApareceUmaVez() {
        Anuncio anuncio = concluido(empregador, "reaplicou", BASE);
        aplicacao(anuncio, prestador, StatusAplicacao.RECUSADA, false);
        aplicacao(anuncio, prestador, StatusAplicacao.ENCERRADA, false);
        aplicacao(anuncio, prestador, StatusAplicacao.CONCLUIDA, true);

        Page<Anuncio> pagina = anuncioService.buscarHistoricoDoPrestador(prestador, SEM_FILTRO, 0, 20);

        assertThat(titulos(pagina)).containsExactly("reaplicou");
        assertThat(pagina.getTotalElements()).isEqualTo(1);
    }

    @Test
    void dadoAnuncioAindaAbertoComCandidaturaDoPrestador_quandoConsulta_entaoNaoEHistorico() {
        Anuncio aberto = anuncio(empregador, "aberto", StatusAnuncio.PUBLICADO, null, null, null);
        aplicacao(aberto, prestador, StatusAplicacao.ENVIADA, false);

        assertThat(anuncioService.buscarHistoricoDoPrestador(prestador, SEM_FILTRO, 0, 20).getContent()).isEmpty();
    }

    @Test
    void dadoFiltrosDeStatusEPeriodo_quandoPrestadorConsulta_entaoMesmasRegrasDoEmpregador() {
        Anuncio c = concluido(empregador, "concluido", BASE);
        Anuncio x = cancelado(empregador, "cancelado", BASE.minus(Duration.ofDays(40)));
        aplicacao(c, prestador, StatusAplicacao.CONCLUIDA, true);
        aplicacao(x, prestador, StatusAplicacao.ENCERRADA, false);

        assertThat(titulos(anuncioService.buscarHistoricoDoPrestador(prestador,
                new CriterioDoHistorico(StatusHistorico.CANCELADO, null, null), 0, 20))).containsExactly("cancelado");
        assertThat(titulos(anuncioService.buscarHistoricoDoPrestador(prestador,
                new CriterioDoHistorico(null, BASE.minus(Duration.ofDays(5)), null), 0, 20)))
                .containsExactly("concluido");
        assertThat(anuncioService.buscarHistoricoDoPrestador(prestador,
                new CriterioDoHistorico(StatusHistorico.EXPIRADO, null, null), 0, 20).getContent()).isEmpty();
    }

    // ---- invariante status x causa

    @Test
    void dadoEncerradoSemCausa_quandoSalvar_entaoBancoRecusa() {
        assertThatThrownBy(() -> anuncio(empregador, "sem causa", StatusAnuncio.ENCERRADO, null, null, BASE))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_anuncio_causa_so_no_encerrado");
    }

    @Test
    void dadoConcluidoComCausa_quandoSalvar_entaoBancoRecusa() {
        assertThatThrownBy(() -> anuncio(empregador, "com causa", StatusAnuncio.CONCLUIDO,
                CausaEncerramento.CANCELAMENTO, BASE, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_anuncio_causa_so_no_encerrado");
    }

    // ---- candidaturas em lote

    @Test
    void dadoCandidaturasEscolhidasECandidatos_quandoBuscaEscolhidasDosAnuncios_entaoSoAsQueForamEscolhidas() {
        Anuncio a1 = concluido(empregador, "a1", BASE);
        Anuncio semEscolha = cancelado(empregador, "sem escolha", BASE);
        aplicacao(a1, prestador, StatusAplicacao.CONCLUIDA, true);
        aplicacao(a1, outroPrestador, StatusAplicacao.ENCERRADA, false);
        aplicacao(semEscolha, prestador, StatusAplicacao.ENCERRADA, false);

        List<Aplicacao> escolhidas = aplicacaoService
                .buscarEscolhidasDosAnuncios(List.of(a1.getId(), semEscolha.getId()));

        assertThat(escolhidas).singleElement().satisfies(a -> {
            assertThat(a.getAnuncioId()).isEqualTo(a1.getId());
            assertThat(a.getPrestadorId()).isEqualTo(prestador);
        });
    }

    @Test
    void dadoListaVazia_quandoBuscaEmLote_entaoListaVaziaSemConsulta() {
        assertThat(aplicacaoService.buscarEscolhidasDosAnuncios(List.of())).isEmpty();
    }
}
