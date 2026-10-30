package com.limio.api.modulos.perfil.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.portas.ConsultaPlanoPrestador;
import com.limio.api.comum.portas.enums.Plano;
import com.limio.api.comum.seguranca.JwtAuthFilter;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;

/**
 * BDD ponta a ponta da consulta da cota do mês (ETI-79): controller → use case → {@code cota_mensal} e, pela porta,
 * {@code aplicacao}/{@code anuncio} no banco real. A escolha (ETI-69/ETI-70) ainda não existe, então as escolhas
 * consumidas e as candidaturas escolhidas entram por SQL. O relógio é trocado pra testar a virada do mês e o plano
 * pra testar o GOLD (a assinatura ainda não existe). O token é emitido com o {@link JwtEncoder}, no formato lido
 * pelo {@link JwtAuthFilter}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CotaDoMesStoryTest {

    private static final String ROTA = "/perfil/prestador/cota";
    /** 29/10/2026, meio-dia em São Paulo. */
    private static final Instant MEIO_DE_OUTUBRO = Instant.parse("2026-10-29T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PerfilPrestadorService perfilPrestadorService;

    @MockitoBean
    private Clock relogio;

    @MockitoBean
    private ConsultaPlanoPrestador consultaPlanoPrestador;

    private UUID prestadorId;

    @BeforeEach
    void prestadorFreeEmOutubro() {
        prestadorId = UUID.randomUUID();
        when(relogio.instant()).thenReturn(MEIO_DE_OUTUBRO);
        when(consultaPlanoPrestador.planoAtual(prestadorId)).thenReturn(Plano.FREE);
    }

    private String token(UUID usuarioId, PapelUsuario papel) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuarioId.toString())
                .claim(JwtAuthFilter.CLAIM_PAPEL, papel.name())
                .issuedAt(agora)
                .expiresAt(agora.plus(5, ChronoUnit.MINUTES))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private ResultActions consultar(UUID usuarioId, PapelUsuario papel) throws Exception {
        return mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(usuarioId, papel)));
    }

    private UUID perfil(UUID usuarioId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into perfil_prestador (id, usuario_id, raio_atendimento_km, municipio_base, uf_base,
                    latitude_base, longitude_base, completo)
                values (?, ?, 10, 'Campinas', 'SP', -22.9, -47.06, true)""", id, usuarioId);
        return id;
    }

    private void cota(UUID perfilId, String anoMes, int consumidas) {
        jdbc.update("insert into cota_mensal (perfil_prestador_id, ano_mes, escolhas_consumidas, teto_aplicado) "
                + "values (?, ?, ?, 4)", perfilId, anoMes, consumidas);
    }

    /** Anúncio de outro usuário em que {@code prestadorId} foi escolhido em {@code escolhidaEm}. */
    private UUID escolhido(UUID prestador, String titulo, Instant escolhidaEm, String statusAplicacao) {
        UUID anuncioId = UUID.randomUUID();
        jdbc.update("""
                insert into anuncio (id, contratante_id, titulo, descricao, data_hora_pretendida, status, categoria_id)
                values (?, ?, ?, ?, ?, 'PUBLICADO', ?)""", anuncioId, UUID.randomUUID(), titulo, titulo,
                Timestamp.from(escolhidaEm.plus(3, ChronoUnit.DAYS)), UUID.randomUUID());
        jdbc.update("""
                insert into aplicacao (anuncio_id, prestador_id, status, data_aplicacao, escolhida_em)
                values (?, ?, ?, ?, ?)""", anuncioId, prestador, statusAplicacao,
                Timestamp.from(escolhidaEm.minus(1, ChronoUnit.DAYS)), Timestamp.from(escolhidaEm));
        return anuncioId;
    }

    @Test
    void dadoPrestadorFreeSemEscolhasNoMes_quandoConsulta_entaoCotaZeradaComTeto4() throws Exception {
        perfil(prestadorId);

        consultar(prestadorId, PapelUsuario.PRESTADOR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plano", is("FREE")))
                .andExpect(jsonPath("$.anoMes", is("2026-10")))
                .andExpect(jsonPath("$.escolhasConsumidas", is(0)))
                .andExpect(jsonPath("$.tetoAplicado", is(4)))
                .andExpect(jsonPath("$.esgotada", is(false)))
                .andExpect(jsonPath("$.renovaEm", is("2026-11-01T00:00:00-03:00")))
                .andExpect(jsonPath("$.escolhas", hasSize(0)));
    }

    @Test
    void dadoPrestadorSemPerfilSalvo_quandoConsulta_entaoCotaZeradaSemCriarNada() throws Exception {
        consultar(prestadorId, PapelUsuario.PRESTADOR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escolhasConsumidas", is(0)))
                .andExpect(jsonPath("$.tetoAplicado", is(4)));
    }

    @Test
    void dadoPrestadorFreeCom2Escolhas_quandoConsulta_entaoMostraAsDuasNaOrdemDaEscolha() throws Exception {
        cota(perfil(prestadorId), "2026-10", 2);
        UUID faxina = escolhido(prestadorId, "Faxina", Instant.parse("2026-10-20T12:00:00Z"), "ESCOLHIDA");
        UUID pintura = escolhido(prestadorId, "Pintura", Instant.parse("2026-10-05T12:00:00Z"), "CONCLUIDA");
        // escolhido em 30/09 23:30 de São Paulo (01/10 em UTC): é do mês passado, fica de fora
        escolhido(prestadorId, "Mês passado", Instant.parse("2026-10-01T02:30:00Z"), "CONCLUIDA");
        // outro Prestador escolhido no mesmo mês: não aparece
        escolhido(UUID.randomUUID(), "De outro", Instant.parse("2026-10-10T12:00:00Z"), "ESCOLHIDA");

        consultar(prestadorId, PapelUsuario.PRESTADOR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escolhasConsumidas", is(2)))
                .andExpect(jsonPath("$.esgotada", is(false)))
                .andExpect(jsonPath("$.escolhas", hasSize(2)))
                .andExpect(jsonPath("$.escolhas[0].anuncioId", is(faxina.toString())))
                .andExpect(jsonPath("$.escolhas[0].titulo", is("Faxina")))
                .andExpect(jsonPath("$.escolhas[0].status", is("ESCOLHIDA")))
                .andExpect(jsonPath("$.escolhas[0].escolhidaEm", is("2026-10-20T12:00:00Z")))
                .andExpect(jsonPath("$.escolhas[0].dataHoraServico", is("2026-10-23T12:00:00Z")))
                .andExpect(jsonPath("$.escolhas[1].anuncioId", is(pintura.toString())))
                .andExpect(jsonPath("$.escolhas[1].status", is("CONCLUIDA")));
    }

    @Test
    void dadoPrestadorFreeCom4Escolhas_quandoConsulta_entaoCotaEsgotada() throws Exception {
        cota(perfil(prestadorId), "2026-10", 4);

        consultar(prestadorId, PapelUsuario.PRESTADOR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escolhasConsumidas", is(4)))
                .andExpect(jsonPath("$.tetoAplicado", is(4)))
                .andExpect(jsonPath("$.esgotada", is(true)));
    }

    @Test
    void dadoPrestadorGold_quandoConsulta_entaoPlanoGoldSemTeto() throws Exception {
        cota(perfil(prestadorId), "2026-10", 4);
        when(consultaPlanoPrestador.planoAtual(prestadorId)).thenReturn(Plano.GOLD);

        consultar(prestadorId, PapelUsuario.PRESTADOR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plano", is("GOLD")))
                .andExpect(jsonPath("$.tetoAplicado", nullValue()))
                .andExpect(jsonPath("$.esgotada", is(false)));
    }

    @Test
    void dadoCotaEsgotadaEmOutubro_quandoViraONovembroEmSaoPaulo_entaoContagemRecomecaDoZero() throws Exception {
        cota(perfil(prestadorId), "2026-10", 4);
        escolhido(prestadorId, "Faxina", Instant.parse("2026-10-20T12:00:00Z"), "CONCLUIDA");

        // 31/10 23:59:59 em São Paulo: ainda outubro
        when(relogio.instant()).thenReturn(Instant.parse("2026-11-01T02:59:59Z"));
        consultar(prestadorId, PapelUsuario.PRESTADOR)
                .andExpect(jsonPath("$.anoMes", is("2026-10")))
                .andExpect(jsonPath("$.escolhasConsumidas", is(4)))
                .andExpect(jsonPath("$.esgotada", is(true)));

        // 01/11 00:00 em São Paulo: mês novo, sem escolha consumida
        when(relogio.instant()).thenReturn(Instant.parse("2026-11-01T03:00:00Z"));
        consultar(prestadorId, PapelUsuario.PRESTADOR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anoMes", is("2026-11")))
                .andExpect(jsonPath("$.escolhasConsumidas", is(0)))
                .andExpect(jsonPath("$.esgotada", is(false)))
                .andExpect(jsonPath("$.renovaEm", is("2026-12-01T00:00:00-03:00")))
                .andExpect(jsonPath("$.escolhas", hasSize(0)));
    }

    @Test
    void dadoPapelEmpregador_quandoConsulta_entao403AcessoNegado() throws Exception {
        consultar(prestadorId, PapelUsuario.EMPREGADOR)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
    }

    @Test
    void dadoPerfilComCota_quandoOPerfilEApagadoNaAnonimizacao_entaoACotaVaiJunto() {
        UUID perfilId = perfil(prestadorId);
        cota(perfilId, "2026-10", 2);

        perfilPrestadorService.apagarDoUsuario(prestadorId);

        assertThat(jdbc.queryForObject("select count(*) from cota_mensal where perfil_prestador_id = ?",
                Integer.class, perfilId)).isZero();
    }

    @Test
    void dadoSemToken_quandoConsulta_entao401() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
    }
}
