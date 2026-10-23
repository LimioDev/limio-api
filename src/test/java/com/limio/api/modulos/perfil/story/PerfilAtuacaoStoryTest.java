package com.limio.api.modulos.perfil.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import com.limio.api.comum.seguranca.JwtAuthFilter;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.perfil.actions.service.Geocodificador;
import com.limio.api.modulos.perfil.categoria.CategoriaRepository;
import com.limio.api.modulos.perfil.enums.Uf;
import com.limio.api.modulos.perfil.perfilprestador.Coordenadas;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorRepository;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.enums.DiaSemana;
import com.limio.api.modulos.perfil.records.JanelaAtendimentoRequest;
import com.limio.api.modulos.perfil.records.PerfilAtuacaoRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do TICKET-0054: listar categorias, consultar e salvar o
 * perfil de atuação. Sobe Spring context + Testcontainers Postgres; as categorias vêm da carga inicial da
 * migration. O token de acesso é emitido com o {@link JwtEncoder} de {@code comum/config} — mesmo formato
 * lido pelo {@link JwtAuthFilter} — sem depender de classe de {@code modulos.auth}. A geocodificação é
 * mockada: o story não chama o Nominatim de verdade (rede e política de uso).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PerfilAtuacaoStoryTest {

    private static final String ROTA = "/perfil/prestador/atuacao";
    private static final Coordenadas CAMPINAS = new Coordenadas(-22.9056391, -47.059564);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private PerfilPrestadorRepository perfilPrestadorRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Geocodificador geocodificador;

    @BeforeEach
    void limparBase() {
        perfilPrestadorRepository.deleteAll();
        when(geocodificador.localizarMunicipio("Campinas", Uf.SP)).thenReturn(Optional.of(CAMPINAS));
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

    private ResultActions consultar(UUID usuarioId) throws Exception {
        return mockMvc.perform(get(ROTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(usuarioId, PapelUsuario.PRESTADOR)));
    }

    private ResultActions salvar(UUID usuarioId, PapelUsuario papel, PerfilAtuacaoRequest request) throws Exception {
        return mockMvc.perform(put(ROTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(usuarioId, papel))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions salvar(UUID usuarioId, PerfilAtuacaoRequest request) throws Exception {
        return salvar(usuarioId, PapelUsuario.PRESTADOR, request);
    }

    private UUID categoria(String nome) {
        return categoriaRepository.findAll().stream()
                .filter(categoria -> categoria.getNome().equals(nome))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private static JanelaAtendimentoRequest janela(DiaSemana dia, int horaInicio, int horaFim) {
        return new JanelaAtendimentoRequest(dia, LocalTime.of(horaInicio, 0), LocalTime.of(horaFim, 0));
    }

    private PerfilAtuacaoRequest perfilCompleto() {
        return new PerfilAtuacaoRequest(List.of(categoria("Elétrica")), 10, "Campinas", Uf.SP,
                List.of(janela(DiaSemana.SEGUNDA, 8, 12)));
    }

    private int janelasGravadas() {
        return jdbcTemplate.queryForObject("select count(*) from janela_atendimento", Integer.class);
    }

    @Test
    void dadoCargaInicial_quandoListaCategorias_entaoDevolveAsAtivasEmOrdemComOutrosPorUltimo() throws Exception {
        String corpo = mockMvc.perform(get("/categorias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(UUID.randomUUID(), PapelUsuario.PRESTADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome", is("Elétrica")))
                .andExpect(jsonPath("$[0].grupo", is("Casa e reparos")))
                .andReturn().getResponse().getContentAsString();

        JsonNode categorias = objectMapper.readTree(corpo);
        assertThat(categorias.get(categorias.size() - 1).get("nome").asString()).isEqualTo("Outros");
    }

    @Test
    void dadoPrestadorQueNuncaSalvou_quandoConsulta_entaoRecebePerfilVazioEIncompleto() throws Exception {
        consultar(UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidades", empty()))
                .andExpect(jsonPath("$.janelas", empty()))
                .andExpect(jsonPath("$.raioAtendimentoKm", nullValue()))
                .andExpect(jsonPath("$.municipioBase", nullValue()))
                .andExpect(jsonPath("$.completo", is(false)));

        assertThat(perfilPrestadorRepository.count()).isZero();
    }

    @Test
    void dadoPerfilCompleto_quandoSalva_entaoRecebe200ComCompletoEAConsultaDevolveOMesmo() throws Exception {
        UUID usuarioId = UUID.randomUUID();

        salvar(usuarioId, perfilCompleto())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidades[0].nome", is("Elétrica")))
                .andExpect(jsonPath("$.raioAtendimentoKm", is(10)))
                .andExpect(jsonPath("$.municipioBase", is("Campinas")))
                .andExpect(jsonPath("$.ufBase", is("SP")))
                .andExpect(jsonPath("$.janelas[0].diaSemana", is("SEGUNDA")))
                .andExpect(jsonPath("$.janelas[0].horaInicio", is("08:00")))
                .andExpect(jsonPath("$.janelas[0].horaFim", is("12:00")))
                .andExpect(jsonPath("$.completo", is(true)));

        consultar(usuarioId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidades[0].nome", is("Elétrica")))
                .andExpect(jsonPath("$.janelas", hasSize(1)))
                .andExpect(jsonPath("$.completo", is(true)));

        PerfilPrestador gravado = perfilPrestadorRepository.findByUsuarioId(usuarioId).orElseThrow();
        assertThat(gravado.getCoordenadasBase()).isEqualTo(CAMPINAS);
    }

    // Regressão: especialidades e janelas no mesmo fetch join viravam produto cartesiano (2 x 2 = 4 janelas).
    @Test
    void dadoPerfilComVariasEspecialidadesEJanelas_quandoConsulta_entaoNaoRepeteJanelas() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        salvar(usuarioId, new PerfilAtuacaoRequest(List.of(categoria("Pintura"), categoria("Elétrica")), 10,
                "Campinas", Uf.SP, List.of(janela(DiaSemana.QUARTA, 8, 12), janela(DiaSemana.SEGUNDA, 8, 12))))
                .andExpect(status().isOk());

        consultar(usuarioId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidades", hasSize(2)))
                .andExpect(jsonPath("$.especialidades[0].nome", is("Elétrica")))
                .andExpect(jsonPath("$.especialidades[1].nome", is("Pintura")))
                .andExpect(jsonPath("$.janelas", hasSize(2)))
                .andExpect(jsonPath("$.janelas[0].diaSemana", is("SEGUNDA")))
                .andExpect(jsonPath("$.janelas[1].diaSemana", is("QUARTA")));
    }

    @Test
    void dadoPerfilSemEspecialidades_quandoSalva_entaoAceitaMasOPerfilFicaIncompleto() throws Exception {
        salvar(UUID.randomUUID(), new PerfilAtuacaoRequest(List.of(), 10, "Campinas", Uf.SP, List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidades", empty()))
                .andExpect(jsonPath("$.completo", is(false)));

        assertThat(perfilPrestadorRepository.count()).isEqualTo(1);
    }

    @Test
    void dadoRaioForaDe1a40_quandoSalva_entaoRecusaComValidacaoFalhouSemGravar() throws Exception {
        for (int raio : new int[] { 0, 41 }) {
            salvar(UUID.randomUUID(), new PerfilAtuacaoRequest(List.of(categoria("Pintura")), raio, "Campinas", Uf.SP,
                    List.of()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        }

        assertThat(perfilPrestadorRepository.count()).isZero();
    }

    @Test
    void dadoJanelaComInicioDepoisDoFim_quandoSalva_entaoRecusaComValidacaoFalhouSemGravar() throws Exception {
        salvar(UUID.randomUUID(), new PerfilAtuacaoRequest(List.of(categoria("Pintura")), 10, "Campinas", Uf.SP,
                List.of(janela(DiaSemana.TERCA, 18, 9))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        assertThat(perfilPrestadorRepository.count()).isZero();
        assertThat(janelasGravadas()).isZero();
    }

    @Test
    void dadoJanelasSobrepostasNoMesmoDia_quandoSalva_entaoRecusaComValidacaoFalhouSemGravar() throws Exception {
        salvar(UUID.randomUUID(), new PerfilAtuacaoRequest(List.of(categoria("Pintura")), 10, "Campinas", Uf.SP,
                List.of(janela(DiaSemana.SEGUNDA, 8, 12), janela(DiaSemana.SEGUNDA, 9, 11))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));

        assertThat(perfilPrestadorRepository.count()).isZero();
        assertThat(janelasGravadas()).isZero();
        verify(geocodificador, never()).localizarMunicipio(any(), any());
    }

    @Test
    void dadoCategoriaQueNaoExiste_quandoSalva_entaoRecusaComCategoriaInvalida() throws Exception {
        salvar(UUID.randomUUID(), new PerfilAtuacaoRequest(List.of(categoria("Elétrica"), UUID.randomUUID()), 10,
                "Campinas", Uf.SP, List.of()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("CATEGORIA_INVALIDA")));

        assertThat(perfilPrestadorRepository.count()).isZero();
    }

    @Test
    void dadoMunicipioQueAGeocodificacaoNaoAcha_quandoSalva_entaoRecusaComMunicipioNaoEncontrado() throws Exception {
        when(geocodificador.localizarMunicipio("Campinass", Uf.SP)).thenReturn(Optional.empty());

        salvar(UUID.randomUUID(), new PerfilAtuacaoRequest(List.of(), 10, "Campinass", Uf.SP, List.of()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("MUNICIPIO_NAO_ENCONTRADO")));

        assertThat(perfilPrestadorRepository.count()).isZero();
    }

    @Test
    void dadoPerfilSalvo_quandoSalvaDeNovo_entaoSubstituiEspecialidadesEJanelasSemGeocodificarDeNovo() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        salvar(usuarioId, new PerfilAtuacaoRequest(List.of(categoria("Elétrica"), categoria("Hidráulica")), 10,
                "Campinas", Uf.SP, List.of(janela(DiaSemana.SEGUNDA, 8, 12), janela(DiaSemana.QUARTA, 8, 12))))
                .andExpect(status().isOk());

        salvar(usuarioId, new PerfilAtuacaoRequest(List.of(categoria("Pintura")), 25, "Campinas", Uf.SP,
                List.of(janela(DiaSemana.SABADO, 9, 13))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidades", hasSize(1)))
                .andExpect(jsonPath("$.especialidades[0].nome", is("Pintura")))
                .andExpect(jsonPath("$.raioAtendimentoKm", is(25)))
                .andExpect(jsonPath("$.janelas", hasSize(1)))
                .andExpect(jsonPath("$.janelas[0].diaSemana", is("SABADO")));

        assertThat(perfilPrestadorRepository.count()).isEqualTo(1);
        assertThat(janelasGravadas()).isEqualTo(1);
        verify(geocodificador, times(1)).localizarMunicipio("Campinas", Uf.SP);
    }

    @Test
    void dadoOutroPrestadorJaComMunicipioResolvido_quandoSalva_entaoReaproveitaAsCoordenadas() throws Exception {
        salvar(UUID.randomUUID(), perfilCompleto()).andExpect(status().isOk());

        salvar(UUID.randomUUID(), perfilCompleto()).andExpect(status().isOk());

        verify(geocodificador, times(1)).localizarMunicipio(any(), any());
    }

    @Test
    void dadoUsuarioComPapelAtivoEmpregador_quandoSalva_entaoAceitaPoisOPerfilEDele() throws Exception {
        salvar(UUID.randomUUID(), PapelUsuario.EMPREGADOR, perfilCompleto())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo", is(true)));
    }

    @Test
    void dadoPerfilDeOutroUsuario_quandoConsulta_entaoNaoEnxergaNada() throws Exception {
        salvar(UUID.randomUUID(), perfilCompleto()).andExpect(status().isOk());

        consultar(UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidades", empty()))
                .andExpect(jsonPath("$.completo", is(false)));
    }

    @Test
    void dadoSemToken_quandoConsultaOuSalva_entaoRecebe401() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
        mockMvc.perform(put(ROTA).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(perfilCompleto())))
                .andExpect(status().isUnauthorized());

        verify(geocodificador, never()).localizarMunicipio(any(), any());
    }
}
