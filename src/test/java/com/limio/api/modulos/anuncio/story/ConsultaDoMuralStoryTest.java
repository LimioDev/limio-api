package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaRepository;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioDoMuralProjecao;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.CriterioDoMural;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

/**
 * Consulta do mural direto no Postgres real (ETI-79): compatibilidade, raio, filtros, ordem, total e
 * primeira foto. Sem HTTP — o endpoint entra no usecase/controller do mural.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ConsultaDoMuralStoryTest {

    // Base do Prestador: centro de Campinas. ~ -22.9056, -47.0608
    private static final double LAT = -22.9056;
    private static final double LON = -47.0608;
    /** Aproximadamente 0,01° de latitude = 1,11 km. */
    private static final double GRAU_POR_KM = 0.01 / 1.11;

    private static final UUID PRESTADOR = UUID.randomUUID();
    private static final UUID EMPREGADOR = UUID.randomUUID();
    private static final UUID ELETRICA = UUID.randomUUID();
    private static final UUID PINTURA = UUID.randomUUID();
    private static final UUID OUTROS = UUID.randomUUID();
    private static final Instant AGORA = Instant.now();

    @Autowired
    private AnuncioService anuncioService;
    @Autowired
    private AnuncioRepository anuncioRepository;
    @Autowired
    private AplicacaoRepository aplicacaoRepository;
    @Autowired
    private MidiaRepository midiaRepository;
    @Autowired
    private MidiaService midiaService;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limparBase() {
        aplicacaoRepository.deleteAll();
        midiaRepository.deleteAll();
        anuncioRepository.deleteAll();
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private Anuncio anuncio(String titulo, UUID categoria, double kmAoNorte, StatusAnuncio status, UUID contratante) {
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(contratante)
                .titulo(titulo)
                .categoriaId(categoria)
                .descricao(titulo)
                .dataHoraPretendida(AGORA.plus(Duration.ofDays(3)))
                .bairroRegiaoSnapshot("Cambuí")
                .enderecoCompletoSnapshot("Rua Secreta, 100 - Cambuí, Campinas - SP")
                .latitudeSnapshot(LAT + kmAoNorte * GRAU_POR_KM)
                .longitudeSnapshot(LON)
                .status(status)
                .build());
    }

    private Anuncio publicado(String titulo, UUID categoria, double kmAoNorte) {
        return anuncio(titulo, categoria, kmAoNorte, StatusAnuncio.PUBLICADO, EMPREGADOR);
    }

    private CriterioDoMural criterio(Set<UUID> especialidades, int raioKm) {
        return new CriterioDoMural(PRESTADOR, especialidades, OUTROS, LAT, LON, raioKm, Set.of(), AGORA, null);
    }

    private List<String> titulos(Page<AnuncioDoMuralProjecao> pagina) {
        return pagina.getContent().stream().map(AnuncioDoMuralProjecao::getTitulo).toList();
    }

    private Page<AnuncioDoMuralProjecao> buscar(CriterioDoMural criterio) {
        return anuncioService.buscarMural(criterio, PageRequest.of(0, 20));
    }

    @Test
    void dadoAnunciosDeEspecialidadeDentroEForaDoRaio_quandoBuscaMural_entaoSoOsDentroOrdenadosPorDistancia() {
        publicado("longe-8km", ELETRICA, 8);
        publicado("perto-2km", ELETRICA, 2);
        publicado("fora-20km", ELETRICA, 20);

        Page<AnuncioDoMuralProjecao> pagina = buscar(criterio(Set.of(ELETRICA), 10));

        assertThat(titulos(pagina)).containsExactly("perto-2km", "longe-8km");
        assertThat(pagina.getContent().get(0).getDistanciaKm()).isBetween(1.9, 2.1);
        assertThat(pagina.getTotalElements()).isEqualTo(2);
    }

    @Test
    void dadoAnuncioNoLimiteDoRaio_quandoBuscaMural_entaoEntraSoSeDistanciaMenorOuIgual() {
        publicado("dentro-9_5km", ELETRICA, 9.5);
        publicado("fora-10_5km", ELETRICA, 10.5);

        assertThat(titulos(buscar(criterio(Set.of(ELETRICA), 10)))).containsExactly("dentro-9_5km");
    }

    @Test
    void dadoCategoriaForaDasEspecialidades_quandoBuscaMural_entaoNaoAparece() {
        publicado("pintura", PINTURA, 1);

        assertThat(buscar(criterio(Set.of(ELETRICA), 10)).getContent()).isEmpty();
    }

    @Test
    void dadoAnuncioDeCategoriaOutros_quandoBuscaMural_entaoApareceParaQualquerEspecialidade() {
        publicado("outros", OUTROS, 1);

        assertThat(titulos(buscar(criterio(Set.of(ELETRICA), 10)))).containsExactly("outros");
    }

    @Test
    void dadoPrestadorComOutrosNasEspecialidades_quandoBuscaMural_entaoVeAnunciosDeQualquerCategoria() {
        publicado("pintura", PINTURA, 1);
        publicado("eletrica", ELETRICA, 2);

        assertThat(titulos(buscar(criterio(Set.of(OUTROS), 10)))).containsExactly("pintura", "eletrica");
    }

    @Test
    void dadoSemEspecialidades_quandoBuscaMural_entaoPaginaVaziaSemErro() {
        publicado("outros", OUTROS, 1);

        Page<AnuncioDoMuralProjecao> pagina = buscar(criterio(Set.of(), 10));

        assertThat(pagina.getContent()).isEmpty();
        assertThat(pagina.getTotalElements()).isZero();
    }

    @Test
    void dadoAnunciosEmOutrosStatusEDoProprioPrestador_quandoBuscaMural_entaoSoPublicadosDeOutros() {
        publicado("publicado", ELETRICA, 1);
        anuncio("encerrado", ELETRICA, 1, StatusAnuncio.ENCERRADO, EMPREGADOR);
        anuncio("em-andamento", ELETRICA, 1, StatusAnuncio.EM_ANDAMENTO, EMPREGADOR);
        anuncio("meu", ELETRICA, 1, StatusAnuncio.PUBLICADO, PRESTADOR);

        assertThat(titulos(buscar(criterio(Set.of(ELETRICA), 10)))).containsExactly("publicado");
    }

    @Test
    void dadoAnuncioComCandidaturaEscolhida_quandoBuscaMural_entaoNaoAparece_masEnviadaNaoImpede() {
        Anuncio escolhido = publicado("escolhido", ELETRICA, 1);
        Anuncio comCandidato = publicado("com-candidato", ELETRICA, 2);
        aplicacao(escolhido.getId(), StatusAplicacao.ESCOLHIDA);
        aplicacao(comCandidato.getId(), StatusAplicacao.ENVIADA);

        assertThat(titulos(buscar(criterio(Set.of(ELETRICA), 10)))).containsExactly("com-candidato");
    }

    @Test
    void dadoFiltroDeCategoria_quandoBuscaMural_entaoReduzSoDentroDoCompativel() {
        publicado("eletrica", ELETRICA, 1);
        publicado("pintura", PINTURA, 1);
        publicado("outros", OUTROS, 1);
        CriterioDoMural filtrado = new CriterioDoMural(PRESTADOR, Set.of(ELETRICA, PINTURA), OUTROS, LAT, LON, 10,
                Set.of(PINTURA, UUID.randomUUID()), AGORA, null);

        assertThat(titulos(buscar(filtrado))).containsExactly("pintura");
    }

    @Test
    void dadoJanelaDeData_quandoBuscaMural_entaoCortaPassadoEOQueFicaDepoisDoLimite() {
        Anuncio passado = publicado("passado", ELETRICA, 1);
        jdbc.update("update anuncio set data_hora_pretendida = ? where id = ?",
                java.sql.Timestamp.from(AGORA.minus(Duration.ofHours(1))), passado.getId());
        Anuncio longe = publicado("depois-do-limite", ELETRICA, 1);
        jdbc.update("update anuncio set data_hora_pretendida = ? where id = ?",
                java.sql.Timestamp.from(AGORA.plus(Duration.ofDays(30))), longe.getId());
        publicado("na-janela", ELETRICA, 1);
        CriterioDoMural comLimite = new CriterioDoMural(PRESTADOR, Set.of(ELETRICA), OUTROS, LAT, LON, 10, Set.of(),
                AGORA, AGORA.plus(Duration.ofDays(7)));

        assertThat(titulos(buscar(comLimite))).containsExactly("na-janela");
        assertThat(titulos(buscar(criterio(Set.of(ELETRICA), 10)))).containsExactlyInAnyOrder("na-janela",
                "depois-do-limite");
    }

    @Test
    void dadoMaisAnunciosQueOTamanhoDaPagina_quandoPagina_entaoTotalEODoFiltroInteiroEPaginasNaoSeRepetem() {
        for (int i = 1; i <= 5; i++) {
            publicado("a" + i, ELETRICA, i);
        }
        CriterioDoMural criterio = criterio(Set.of(ELETRICA), 10);

        Page<AnuncioDoMuralProjecao> primeira = anuncioService.buscarMural(criterio, PageRequest.of(0, 2));
        Page<AnuncioDoMuralProjecao> terceira = anuncioService.buscarMural(criterio, PageRequest.of(2, 2));

        assertThat(titulos(primeira)).containsExactly("a1", "a2");
        assertThat(titulos(terceira)).containsExactly("a5");
        assertThat(primeira.getTotalElements()).isEqualTo(5);
        assertThat(primeira.getTotalPages()).isEqualTo(3);
    }

    @Test
    void dadoAnuncioComTresFotos_quandoBuscaPrimeirasFotos_entaoUmaConsultaTrazAMaisAntigaDeCadaAnuncio() throws Exception {
        Anuncio comFotos = publicado("com-fotos", ELETRICA, 1);
        Anuncio semFotos = publicado("sem-fotos", ELETRICA, 2);
        for (String chave : List.of("a1", "a2", "a3")) {
            midiaRepository.saveAndFlush(Midia.fotoAnuncio(EMPREGADOR, comFotos.getId(), "fotos-anuncio/" + chave,
                    "image/jpeg", 10, 10, 100));
            Thread.sleep(5); // criado_em distinto: a "primeira" é a mais antiga
        }

        Map<UUID, Midia> primeiras = midiaService.buscarPrimeirasFotosDosAnuncios(
                List.of(comFotos.getId(), semFotos.getId()));

        assertThat(primeiras).containsOnlyKeys(comFotos.getId());
        assertThat(primeiras.get(comFotos.getId()).getUrlS3()).isEqualTo("fotos-anuncio/a1");
    }

    private void aplicacao(UUID anuncioId, StatusAplicacao status) {
        aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(UUID.randomUUID())
                .status(status)
                .dataAplicacao(Instant.now())
                .build());
    }
}
