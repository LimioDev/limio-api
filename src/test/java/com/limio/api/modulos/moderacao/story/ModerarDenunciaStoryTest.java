package com.limio.api.modulos.moderacao.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaRepository;
import com.limio.api.comum.seguranca.TokensDeTeste;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.administracao.administrador.Administrador;
import com.limio.api.modulos.administracao.administrador.AdministradorRepository;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.auth.actions.usecase.SuspenderContaUseCase;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.moderacao.avaliacao.Avaliacao;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoRepository;
import com.limio.api.modulos.moderacao.denuncia.AlvoDenuncia;
import com.limio.api.modulos.moderacao.denuncia.Denuncia;
import com.limio.api.modulos.moderacao.denuncia.DenunciaRepository;
import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta do ETI-116: fila, análise e decisão de denúncias pelo administrador, com cada um dos quatro tipos
 * de sanção (mesmo os que o painel v1 não expõe), o improcedente e os efeitos em {@code auth}, {@code anuncio} e
 * {@code perfil}. O token do administrador sai de {@link TokensDeTeste} (sem rota de login ainda, US60). O storage é
 * mock.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ModerarDenunciaStoryTest {

    private static final String SENHA = "senhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdministradorRepository administradorRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    @Autowired
    private DenunciaRepository denunciaRepository;

    @Autowired
    private AvaliacaoRepository avaliacaoRepository;

    @Autowired
    private MidiaRepository midiaRepository;

    @Autowired
    private SuspenderContaUseCase suspenderContaUseCase;

    @MockitoBean
    private ArmazenamentoService armazenamentoService;

    private Administrador admin;
    private Usuario maria;
    private Usuario carlos;
    private Usuario joana;

    @BeforeEach
    void prepararBase() {
        limparBase();
        when(armazenamentoService.gerarLinkSeDisponivel(anyString()))
                .thenAnswer(chamada -> Optional.of(new LinkTemporario("https://storage.test/" + chamada.getArgument(0),
                        Instant.now().plus(Duration.ofMinutes(10)))));
        admin = administradorRepository.save(
                Administrador.cadastrar("Admin Limio", "admin@limio.com.br", passwordEncoder.encode(SENHA)));
        maria = usuario("Maria da Silva", "maria@example.com", "52998224725", PapelUsuario.EMPREGADOR);
        carlos = usuario("Carlos Souza", "carlos@example.com", "11144477735", PapelUsuario.PRESTADOR);
        joana = usuario("Joana Lima", "joana@example.com", "39053344705", PapelUsuario.PRESTADOR);
    }

    @AfterEach
    void limparBase() {
        jdbc.execute("delete from log_auditoria");
        jdbc.execute("delete from sancao");
        jdbc.execute("delete from decisao_moderacao");
        jdbc.execute("delete from denuncia");
        jdbc.execute("delete from avaliacao");
        jdbc.execute("delete from midia");
        jdbc.execute("delete from mensagem");
        jdbc.execute("delete from chat");
        jdbc.execute("delete from aplicacao");
        jdbc.execute("delete from anuncio");
        jdbc.execute("delete from cpf_bloqueado");
        jdbc.execute("TRUNCATE TABLE usuario CASCADE");
        jdbc.execute("delete from administrador");
    }

    // ---------------------------------------------------------------- fila

    @Test
    void filaListaSoAsAbertasDoPrazoMaisCurtoParaOMaisLongo() throws Exception {
        Denuncia depois = denuncia(maria, carlos, MotivoDenuncia.OUTRO_MOTIVO, Duration.ofHours(48));
        Denuncia antes = denuncia(joana, carlos, MotivoDenuncia.SUSPEITA_DE_FRAUDE, Duration.ofHours(10));
        Denuncia decidida = denuncia(maria, joana, MotivoDenuncia.COMPORTAMENTO_INADEQUADO, Duration.ofHours(5));
        decidir(decidida, improcedente()).andExpect(status().isOk());

        comoAdmin(get("/admin/denuncias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos", is(2)))
                .andExpect(jsonPath("$.content[0].id", is(antes.getId().toString())))
                .andExpect(jsonPath("$.content[0].numero", is(antes.getNumero().intValue())))
                .andExpect(jsonPath("$.content[0].motivo", is("SUSPEITA_DE_FRAUDE")))
                .andExpect(jsonPath("$.content[0].severidade", is("ALTA")))
                .andExpect(jsonPath("$.content[0].denunciadoNome", is("Carlos Souza")))
                .andExpect(jsonPath("$.content[0].prazoAnalise", notNullValue()))
                .andExpect(jsonPath("$.content[0].criadaEm", notNullValue()))
                .andExpect(jsonPath("$.content[1].id", is(depois.getId().toString())))
                .andExpect(jsonPath("$.content[1].severidade", is("BAIXA")));
    }

    @Test
    void filaFiltraPorSeveridadeEMotivo() throws Exception {
        denuncia(maria, carlos, MotivoDenuncia.OUTRO_MOTIVO, Duration.ofHours(48));
        Denuncia fraude = denuncia(joana, carlos, MotivoDenuncia.SUSPEITA_DE_FRAUDE, Duration.ofHours(10));

        comoAdmin(get("/admin/denuncias").param("severidade", "ALTA"))
                .andExpect(jsonPath("$.totalElementos", is(1)))
                .andExpect(jsonPath("$.content[0].id", is(fraude.getId().toString())));
        comoAdmin(get("/admin/denuncias").param("motivo", "OUTRO_MOTIVO").param("severidade", "ALTA"))
                .andExpect(jsonPath("$.totalElementos", is(0)));
    }

    // ---------------------------------------------------------------- análise

    @Test
    void analisaComChatOriginalFotosEHistoricoSemDadosDeContato() throws Exception {
        Anuncio servico = anuncio(StatusAnuncio.EM_ANDAMENTO);
        UUID candidaturaId = candidatura(servico, carlos, StatusAplicacao.ESCOLHIDA);
        UUID chatId = chat(servico, candidaturaId);
        mensagem(chatId, carlos, "Me passa seu PIX 11988887777 antes", "Me passa seu PIX *** antes");
        Midia evidencia = midiaRepository.save(Midia.evidenciaDenuncia(maria.getId(), "evidencias-denuncia/prova.png",
                "image/png", 20, 20, 100));
        midiaRepository.save(Midia.evidenciaCheckout(carlos.getId(), servico.getId(), "checkouts/foto.png",
                "image/png", 20, 20, 100));
        Denuncia anterior = denuncia(joana, carlos, MotivoDenuncia.COMPORTAMENTO_INADEQUADO, Duration.ofHours(72));
        decidir(anterior, advertencia()).andExpect(status().isOk());
        Denuncia denuncia = denunciaRepository.save(Denuncia.registrar(maria.getId(),
                new AlvoDenuncia(carlos.getId(), servico.getId(), candidaturaId, null), MotivoDenuncia.SUSPEITA_DE_FRAUDE,
                "Pediu pagamento adiantado.", evidencia.getId(), Instant.now().plus(Duration.ofHours(72))));

        comoAdmin(get("/admin/denuncias/{id}", denuncia.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero", is(denuncia.getNumero().intValue())))
                .andExpect(jsonPath("$.motivo", is("SUSPEITA_DE_FRAUDE")))
                .andExpect(jsonPath("$.descricao", is("Pediu pagamento adiantado.")))
                .andExpect(jsonPath("$.statusResolucao", is("ABERTA")))
                .andExpect(jsonPath("$.denunciante.nome", is("Maria da Silva")))
                .andExpect(jsonPath("$.denunciante.papel", is("EMPREGADOR")))
                .andExpect(jsonPath("$.denunciado.nome", is("Carlos Souza")))
                .andExpect(jsonPath("$.denunciado.situacao", is("ATIVA")))
                .andExpect(jsonPath("$.alvo.tipo", is("CANDIDATURA")))
                .andExpect(jsonPath("$.alvo.tituloAnuncio", is("Instalar 3 tomadas")))
                .andExpect(jsonPath("$.evidencia.url", is("https://storage.test/evidencias-denuncia/prova.png")))
                .andExpect(jsonPath("$.fotosCheckout", hasSize(1)))
                .andExpect(jsonPath("$.chat", hasSize(1)))
                .andExpect(jsonPath("$.chat[0].texto", is("Me passa seu PIX 11988887777 antes")))
                .andExpect(jsonPath("$.chat[0].remetenteId", is(carlos.getId().toString())))
                .andExpect(jsonPath("$.outrasDenuncias", hasSize(1)))
                .andExpect(jsonPath("$.outrasDenuncias[0].statusResolucao", is("PROCEDENTE")))
                .andExpect(jsonPath("$.sancoesAnteriores", hasSize(1)))
                .andExpect(jsonPath("$.sancoesAnteriores[0].tipo", is("ADVERTENCIA")))
                .andExpect(jsonPath("$.decisao", nullValue()))
                .andExpect(content().string(not(containsString("carlos@example.com"))))
                .andExpect(content().string(not(containsString("11144477735"))))
                .andExpect(content().string(not(containsString("maria@example.com"))));
    }

    @Test
    void analisaDenunciaDeServicoPeloChatDoPrestadorEscolhido() throws Exception {
        Anuncio servico = anuncio(StatusAnuncio.CONCLUIDO);
        UUID candidaturaId = candidatura(servico, carlos, StatusAplicacao.CONCLUIDA);
        mensagem(chat(servico, candidaturaId), maria, "Te pago amanhã", "Te pago amanhã");
        Denuncia denuncia = denunciaRepository.save(Denuncia.registrar(carlos.getId(),
                new AlvoDenuncia(maria.getId(), servico.getId(), null, null), MotivoDenuncia.NAO_PAGOU_VALOR_COMBINADO,
                "Não pagou.", null, Instant.now().plus(Duration.ofHours(72))));

        comoAdmin(get("/admin/denuncias/{id}", denuncia.getId()))
                .andExpect(jsonPath("$.alvo.tipo", is("SERVICO")))
                .andExpect(jsonPath("$.chat[0].texto", is("Te pago amanhã")))
                .andExpect(jsonPath("$.evidencia", nullValue()));
    }

    @Test
    void analisarDenunciaInexistenteResponde404() throws Exception {
        comoAdmin(get("/admin/denuncias/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo", is("DENUNCIA_NAO_ENCONTRADA")));
    }

    // ---------------------------------------------------------------- decisões

    @Test
    void improcedenteFechaSemSancao() throws Exception {
        Denuncia denuncia = denuncia(maria, carlos, MotivoDenuncia.OUTRO_MOTIVO, Duration.ofHours(72));

        decidir(denuncia, improcedente())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusResolucao", is("IMPROCEDENTE")))
                .andExpect(jsonPath("$.analisadaEm", notNullValue()))
                .andExpect(jsonPath("$.decisao.veredito", is("IMPROCEDENTE")))
                .andExpect(jsonPath("$.decisao.administradorId", is(admin.getId().toString())))
                .andExpect(jsonPath("$.decisao.sancoes", hasSize(0)));

        assertThat(jdbc.queryForObject("select count(*) from sancao", Integer.class)).isZero();
        assertThat(statusConta(carlos)).isEqualTo("ATIVA");
        Map<String, Object> log = jdbc.queryForMap("select * from log_auditoria where tipo_acao = 'DENUNCIA_DECIDIDA'");
        assertThat(log.get("entidade_id")).isEqualTo(denuncia.getId());
        assertThat((String) log.get("detalhes_json")).contains("IMPROCEDENTE", admin.getId().toString());
    }

    @Test
    void advertenciaGravaASancaoComOFundamentoComoMotivo() throws Exception {
        Denuncia denuncia = denuncia(maria, carlos, MotivoDenuncia.COMPORTAMENTO_INADEQUADO, Duration.ofHours(72));

        decidir(denuncia, advertencia())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusResolucao", is("PROCEDENTE")))
                .andExpect(jsonPath("$.decisao.sancoes[0].tipo", is("ADVERTENCIA")))
                .andExpect(jsonPath("$.decisao.sancoes[0].motivo", is("Linguagem ofensiva no chat.")));

        Map<String, Object> sancao = jdbc.queryForMap("select * from sancao");
        assertThat(sancao.get("usuario_id")).isEqualTo(carlos.getId());
        assertThat(sancao.get("inicio_em")).isNull();
        assertThat(statusConta(carlos)).isEqualTo("ATIVA");
        comoAdmin(get("/admin/denuncias")).andExpect(jsonPath("$.totalElementos", is(0)));
    }

    @Test
    void ocultarAvaliacaoRecalculaAMediaSemEla() throws Exception {
        Anuncio primeiro = anuncio(StatusAnuncio.CONCLUIDO);
        Anuncio segundo = anuncio(StatusAnuncio.CONCLUIDO);
        Avaliacao ofensiva = avaliacao(primeiro, carlos, maria, 1, "Péssima, xingou todo mundo");
        avaliacao(segundo, joana, maria, 5, "Ótima");
        Denuncia denuncia = denuncia(maria, carlos, MotivoDenuncia.COMPORTAMENTO_INADEQUADO, Duration.ofHours(72));

        decidir(denuncia, Map.of("veredito", "PROCEDENTE", "fundamento", "Avaliação com ofensa.",
                "sancao", "OCULTAR_AVALIACAO", "avaliacaoId", ofensiva.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisao.sancoes[0].avaliacaoId", is(ofensiva.getId().toString())));

        Map<String, Object> avaliacao = jdbc.queryForMap("select * from avaliacao where id = ?", ofensiva.getId());
        assertThat(avaliacao.get("oculta_em")).isNotNull();
        assertThat(avaliacao.get("oculta_por")).isEqualTo(admin.getId());
        Map<String, Object> perfil = jdbc.queryForMap("select * from perfil_empregador where usuario_id = ?",
                maria.getId());
        assertThat((BigDecimal) perfil.get("media_avaliacoes")).isEqualByComparingTo("5.0");
        assertThat(perfil.get("qtd_avaliacoes")).isEqualTo(1);
    }

    @Test
    void naoOcultaAvaliacaoQueODenunciadoNaoEscreveu() throws Exception {
        Avaliacao daJoana = avaliacao(anuncio(StatusAnuncio.CONCLUIDO), joana, maria, 2, "Fraca");
        Denuncia denuncia = denuncia(maria, carlos, MotivoDenuncia.COMPORTAMENTO_INADEQUADO, Duration.ofHours(72));

        decidir(denuncia, Map.of("veredito", "PROCEDENTE", "fundamento", "Avaliação com ofensa.",
                "sancao", "OCULTAR_AVALIACAO", "avaliacaoId", daJoana.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        assertThat(jdbc.queryForObject("select status_resolucao from denuncia", String.class)).isEqualTo("ABERTA");
    }

    @Test
    void suspensaoBloqueiaAContaEncerraSessoesEAnunciosAbertos() throws Exception {
        Anuncio servicoDaMaria = anuncio(StatusAnuncio.EM_ANDAMENTO);
        UUID escolhida = candidatura(servicoDaMaria, carlos, StatusAplicacao.ESCOLHIDA);
        Anuncio outroDaMaria = anuncio(StatusAnuncio.PUBLICADO);
        UUID enviada = candidatura(outroDaMaria, carlos, StatusAplicacao.ENVIADA);
        bearer(carlos); // abre uma sessão
        Denuncia denuncia = denuncia(maria, carlos, MotivoDenuncia.SUSPEITA_DE_FRAUDE, Duration.ofHours(72));
        Instant inicio = Instant.now().minus(Duration.ofMinutes(1));
        Instant fim = Instant.now().plus(Duration.ofDays(7));

        decidir(denuncia, Map.of("veredito", "PROCEDENTE", "fundamento", "Golpe confirmado.",
                "sancao", "SUSPENSAO", "inicioEm", inicio.toString(), "fimEm", fim.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisao.sancoes[0].tipo", is("SUSPENSAO")))
                .andExpect(jsonPath("$.decisao.sancoes[0].fimEm", notNullValue()));

        assertThat(statusConta(carlos)).isEqualTo("BLOQUEADA");
        assertThat(jdbc.queryForObject("select count(*) from sessao where usuario_id = ? and revogada_em is null",
                Integer.class, carlos.getId())).isZero();
        login(carlos).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo", is("CONTA_SUSPENSA")));
        assertThat(statusAplicacao(escolhida)).isEqualTo("ENCERRADA");
        assertThat(statusAplicacao(enviada)).isEqualTo("ENCERRADA");
        assertThat(jdbc.queryForObject("select status from anuncio where id = ?", String.class, servicoDaMaria.getId()))
                .isEqualTo("ENCERRADO");
        assertThat(jdbc.queryForObject("select status from anuncio where id = ?", String.class, outroDaMaria.getId()))
                .as("anúncio de outra pessoa em que ele só se candidatou continua").isEqualTo("PUBLICADO");
    }

    @Test
    void suspensaoVencidaVoltaAContaParaAtiva() throws Exception {
        Denuncia denuncia = denuncia(maria, carlos, MotivoDenuncia.SUSPEITA_DE_FRAUDE, Duration.ofHours(72));
        decidir(denuncia, Map.of("veredito", "PROCEDENTE", "fundamento", "Golpe confirmado.", "sancao", "SUSPENSAO",
                "inicioEm", Instant.now().toString(), "fimEm", Instant.now().plus(Duration.ofDays(1)).toString()))
                .andExpect(status().isOk());
        jdbc.update("update usuario set suspensa_ate = now() - interval '1 minute' where id = ?", carlos.getId());

        suspenderContaUseCase.encerrarSuspensoesVencidas();

        assertThat(statusConta(carlos)).isEqualTo("ATIVA");
        login(carlos).andExpect(status().isOk());
    }

    @Test
    void suspensaoSemPeriodoOuComFimAntesDoInicioERecusada() throws Exception {
        Denuncia denuncia = denuncia(maria, carlos, MotivoDenuncia.SUSPEITA_DE_FRAUDE, Duration.ofHours(72));

        decidir(denuncia, Map.of("veredito", "PROCEDENTE", "fundamento", "Golpe.", "sancao", "SUSPENSAO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        decidir(denuncia, Map.of("veredito", "PROCEDENTE", "fundamento", "Golpe.", "sancao", "SUSPENSAO",
                "inicioEm", Instant.now().plus(Duration.ofDays(2)).toString(),
                "fimEm", Instant.now().plus(Duration.ofDays(1)).toString()))
                .andExpect(status().isBadRequest());

        assertThat(statusConta(carlos)).isEqualTo("ATIVA");
    }

    @Test
    void encerrarContaAnonimizaComoNoPedidoDoTitular() throws Exception {
        Denuncia denuncia = denuncia(maria, carlos, MotivoDenuncia.SUSPEITA_DE_FRAUDE, Duration.ofHours(72));

        decidir(denuncia, Map.of("veredito", "PROCEDENTE", "fundamento", "Fraudes repetidas.",
                "sancao", "ENCERRAMENTO_DE_CONTA"))
                .andExpect(status().isOk());

        Map<String, Object> conta = jdbc.queryForMap("select * from usuario where id = ?", carlos.getId());
        assertThat(conta.get("anonimizado_em")).isNotNull();
        assertThat(conta.get("cpf")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from cpf_bloqueado", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select origem from log_auditoria where tipo_acao = 'CONTA_ENCERRADA'",
                String.class)).isEqualTo("moderacao-decidir-denuncia");
    }

    @Test
    void removeAFotoDePerfilDoDenunciado() throws Exception {
        Midia foto = midiaRepository.save(Midia.fotoPerfil(carlos.getId(), "fotos-perfil/carlos.png", "image/png",
                20, 20, 100));
        Denuncia denuncia = denuncia(maria, carlos, MotivoDenuncia.COMPORTAMENTO_INADEQUADO, Duration.ofHours(72));

        Map<String, Object> corpo = new LinkedHashMap<>(advertencia());
        corpo.put("removerFotoPerfil", true);
        decidir(denuncia, corpo).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select expurgo_em from midia where id = ?", Instant.class, foto.getId()))
                .isNotNull();
        verify(armazenamentoService).remover("fotos-perfil/carlos.png");
    }

    @Test
    void denunciaJaDecididaResponde409() throws Exception {
        Denuncia denuncia = denuncia(maria, carlos, MotivoDenuncia.OUTRO_MOTIVO, Duration.ofHours(72));
        decidir(denuncia, improcedente()).andExpect(status().isOk());

        decidir(denuncia, advertencia())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("DENUNCIA_JA_RESOLVIDA")));
    }

    @Test
    void improcedenteComSancaoOuSemFundamentoERecusado() throws Exception {
        Denuncia denuncia = denuncia(maria, carlos, MotivoDenuncia.OUTRO_MOTIVO, Duration.ofHours(72));

        decidir(denuncia, Map.of("veredito", "IMPROCEDENTE", "fundamento", "Nada.", "sancao", "ADVERTENCIA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        decidir(denuncia, Map.of("veredito", "IMPROCEDENTE", "fundamento", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    // ---------------------------------------------------------------- acesso

    @Test
    void usuarioComumRecebe403EmTodasAsRotas() throws Exception {
        Denuncia denuncia = denuncia(maria, carlos, MotivoDenuncia.OUTRO_MOTIVO, Duration.ofHours(72));
        String token = bearer(maria);

        mockMvc.perform(get("/admin/denuncias").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
        mockMvc.perform(get("/admin/denuncias/{id}", denuncia.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/denuncias/{id}/decisao", denuncia.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(improcedente())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));

        assertThat(jdbc.queryForObject("select status_resolucao from denuncia", String.class)).isEqualTo("ABERTA");
    }

    // ---------------------------------------------------------------- apoio

    private static Map<String, Object> improcedente() {
        return Map.of("veredito", "IMPROCEDENTE", "fundamento", "Sem provas do que foi relatado.");
    }

    private static Map<String, Object> advertencia() {
        return Map.of("veredito", "PROCEDENTE", "fundamento", "Linguagem ofensiva no chat.", "sancao", "ADVERTENCIA");
    }

    private ResultActions decidir(Denuncia denuncia, Map<String, ?> corpo) throws Exception {
        return comoAdmin(post("/admin/denuncias/{id}/decisao", denuncia.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)));
    }

    private ResultActions comoAdmin(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mockMvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + TokensDeTeste.administrador(jwtEncoder, admin.getId())));
    }

    private Denuncia denuncia(Usuario denunciante, Usuario denunciado, MotivoDenuncia motivo, Duration prazo) {
        return denunciaRepository.saveAndFlush(Denuncia.registrar(denunciante.getId(),
                new AlvoDenuncia(denunciado.getId(), null, null, null), motivo, "O que aconteceu.", null,
                Instant.now().plus(prazo)));
    }

    private String statusConta(Usuario usuario) {
        return jdbc.queryForObject("select status_conta from usuario where id = ?", String.class, usuario.getId());
    }

    private String statusAplicacao(UUID id) {
        return jdbc.queryForObject("select status from aplicacao where id = ?", String.class, id);
    }

    /** Anúncio de Maria. */
    private Anuncio anuncio(StatusAnuncio status) {
        return anuncioRepository.save(Anuncio.builder()
                .titulo("Instalar 3 tomadas")
                .categoriaId(jdbc.queryForObject("select id from categoria where protegida", UUID.class))
                .contratanteId(maria.getId())
                .descricao("Trocar três tomadas da cozinha")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(3)))
                .status(status)
                .validadoEm(status == StatusAnuncio.CONCLUIDO ? Instant.now().minus(Duration.ofDays(1)) : null)
                .build());
    }

    private UUID candidatura(Anuncio anuncio, Usuario prestador, StatusAplicacao status) {
        return aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(prestador.getId())
                .status(status)
                .dataAplicacao(Instant.now().minus(Duration.ofDays(2)))
                .escolhidaEm(status == StatusAplicacao.ENVIADA ? null : Instant.now().minus(Duration.ofDays(1)))
                .build()).getId();
    }

    private UUID chat(Anuncio anuncio, UUID candidaturaId) {
        UUID id = UUID.randomUUID();
        UUID prestadorId = jdbc.queryForObject("select prestador_id from aplicacao where id = ?", UUID.class,
                candidaturaId);
        jdbc.update("insert into chat (id, aplicacao_id, anuncio_id, contratante_id, prestador_id) values (?, ?, ?, ?, ?)",
                id, candidaturaId, anuncio.getId(), maria.getId(), prestadorId);
        return id;
    }

    private void mensagem(UUID chatId, Usuario remetente, String texto, String textoExibido) {
        jdbc.update("""
                insert into mensagem (id, chat_id, remetente_id, texto, texto_exibido, data_envio)
                values (?, ?, ?, ?, ?, now())
                """, UUID.randomUUID(), chatId, remetente.getId(), texto, textoExibido);
    }

    private Avaliacao avaliacao(Anuncio anuncio, Usuario avaliador, Usuario avaliado, int nota, String comentario) {
        return avaliacaoRepository.save(Avaliacao.publicar(anuncio.getId(), avaliador.getId(), avaliado.getId(),
                avaliado.getPapelAtivo(), nota, comentario, Instant.now().minus(Duration.ofDays(1))));
    }

    private Usuario usuario(String nome, String email, String cpf, PapelUsuario papel) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nome)
                .email(email)
                .telefone("11988887777")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(30))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(papel)
                .statusConta(StatusConta.ATIVA)
                .emailConfirmadoEm(Instant.now())
                .build());
    }

    private ResultActions login(Usuario usuario) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(usuario.getEmail(), SENHA, "celular"))));
    }

    private String bearer(Usuario usuario) throws Exception {
        String corpo = login(usuario).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readValue(corpo, LoginResponse.class).token();
    }
}
