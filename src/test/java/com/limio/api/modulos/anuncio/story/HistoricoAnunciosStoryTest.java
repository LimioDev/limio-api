package com.limio.api.modulos.anuncio.story;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.CausaEncerramento;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.moderacao.avaliacao.Avaliacao;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller -> banco real) do ETI-71 — histórico de anúncios e serviços com filtros. Anúncios,
 * candidaturas e avaliações nascem pelo repository; as datas do desfecho e a causa são ajustadas por SQL, para os
 * cenários serem determinísticos.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class HistoricoAnunciosStoryTest {

    private static final String ROTA = "/anuncios/historico";
    private static final String SENHA = "senhaForte123";
    private static final String EMAIL_EMPREGADOR = "maria@example.com";
    private static final String EMAIL_PRESTADOR = "rafael@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    @Autowired
    private AvaliacaoRepository avaliacaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario empregador;
    private Usuario prestador;
    private String tokenEmpregador;
    private String tokenPrestador;

    @BeforeEach
    void prepararUsuarios() throws Exception {
        limparBase();
        empregador = cadastrar("Maria da Silva", EMAIL_EMPREGADOR, "11988887777", "52998224725",
                PapelUsuario.EMPREGADOR);
        prestador = cadastrar("Rafael Souza Lima", EMAIL_PRESTADOR, "11999998888", "11144477735",
                PapelUsuario.PRESTADOR);
        tokenEmpregador = logar(EMAIL_EMPREGADOR);
        tokenPrestador = logar(EMAIL_PRESTADOR);
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private void limparBase() {
        jdbc.execute("delete from avaliacao");
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from sessao");
        jdbc.execute("delete from tentativa_login");
        usuarioRepository.deleteAll();
    }

    private Usuario cadastrar(String nomeCompleto, String email, String telefone, String cpf, PapelUsuario papel) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto(nomeCompleto)
                .email(email)
                .telefone(telefone)
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(25))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(papel)
                .statusConta(StatusConta.ATIVA)
                .build());
    }

    private String logar(String email) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, SENHA, "celular"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, LoginResponse.class).token();
    }

    private Anuncio anuncio(UUID contratanteId, String titulo, StatusAnuncio status) {
        return anuncioRepository.save(Anuncio.builder()
                .contratanteId(contratanteId)
                .titulo(titulo)
                .categoriaId(UUID.randomUUID())
                .descricao(titulo)
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(3)))
                .bairroRegiaoSnapshot("Pinheiros")
                .enderecoCompletoSnapshot("Rua dos Pinheiros, 1000, apto 42")
                .status(status)
                .causaEncerramento(status == StatusAnuncio.ENCERRADO ? CausaEncerramento.CANCELAMENTO : null)
                .build());
    }

    private Anuncio concluido(String titulo, Instant validadoEm) {
        return concluido(empregador.getId(), titulo, validadoEm);
    }

    private Anuncio concluido(UUID contratanteId, String titulo, Instant validadoEm) {
        Anuncio anuncio = anuncio(contratanteId, titulo, StatusAnuncio.CONCLUIDO);
        jdbc.update("update anuncio set validado_em = ? where id = ?", Timestamp.from(validadoEm), anuncio.getId());
        return anuncio;
    }

    private Anuncio encerrado(UUID contratanteId, String titulo, CausaEncerramento causa, Instant encerradoEm) {
        Anuncio anuncio = anuncio(contratanteId, titulo, StatusAnuncio.ENCERRADO);
        jdbc.update("update anuncio set encerrado_em = ?, causa_encerramento = ? where id = ?",
                Timestamp.from(encerradoEm), causa.name(), anuncio.getId());
        return anuncio;
    }

    private Anuncio cancelado(String titulo, Instant encerradoEm) {
        return encerrado(empregador.getId(), titulo, CausaEncerramento.CANCELAMENTO, encerradoEm);
    }

    private Anuncio expirado(String titulo, Instant encerradoEm) {
        return encerrado(empregador.getId(), titulo, CausaEncerramento.EXPIRACAO, encerradoEm);
    }

    private Aplicacao candidatura(Anuncio anuncio, Usuario quem, boolean escolhido) {
        Aplicacao aplicacao = aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncio.getId())
                .prestadorId(quem.getId())
                .status(escolhido ? StatusAplicacao.ESCOLHIDA : StatusAplicacao.RECUSADA)
                .dataAplicacao(Instant.now().minus(Duration.ofDays(30)))
                .build());
        if (escolhido) {
            jdbc.update("update aplicacao set escolhida_em = ? where id = ?",
                    Timestamp.from(Instant.now().minus(Duration.ofDays(20))), aplicacao.getId());
        }
        return aplicacao;
    }

    private void avaliar(Anuncio anuncio, Usuario avaliador, Usuario avaliado, PapelUsuario papelAvaliado, int nota) {
        avaliacaoRepository.save(Avaliacao.publicar(anuncio.getId(), avaliador.getId(), avaliado.getId(),
                papelAvaliado, nota, null, Instant.now()));
    }

    private ResultActions comoEmpregador(String query) throws Exception {
        return mockMvc.perform(get(ROTA + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmpregador));
    }

    private ResultActions comoPrestador(String query) throws Exception {
        return mockMvc.perform(get(ROTA + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPrestador));
    }

    private static Instant dia(String isoComHora) {
        return Instant.parse(isoComHora);
    }

    // ---------- Empregador: status ----------

    @Test
    void dadoAnunciosEmVariosEstados_quandoEmpregadorListaSemFiltro_entaoSoFinaisDoMaisRecenteParaOMaisAntigo()
            throws Exception {
        concluido("Concluído", dia("2026-10-12T17:40:00Z"));
        cancelado("Cancelado", dia("2026-10-20T12:00:00Z"));
        expirado("Expirado", dia("2026-10-03T12:00:00Z"));
        anuncio(empregador.getId(), "Publicado", StatusAnuncio.PUBLICADO);
        anuncio(empregador.getId(), "Em andamento", StatusAnuncio.EM_ANDAMENTO);
        anuncio(empregador.getId(), "Aguardando", StatusAnuncio.AGUARDANDO_VALIDACAO);

        comoEmpregador("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos", is(3)))
                .andExpect(jsonPath("$.content[*].titulo", contains("Cancelado", "Concluído", "Expirado")))
                .andExpect(jsonPath("$.content[*].status", contains("CANCELADO", "CONCLUIDO", "EXPIRADO")))
                .andExpect(jsonPath("$.content[0].data", is("2026-10-20T12:00:00Z")));
    }

    @Test
    void dadoAnunciosFinais_quandoEmpregadorFiltraConcluido_entaoSoConcluidos() throws Exception {
        concluido("Concluído", dia("2026-10-12T17:40:00Z"));
        cancelado("Cancelado", dia("2026-10-20T12:00:00Z"));
        expirado("Expirado", dia("2026-10-03T12:00:00Z"));

        comoEmpregador("?status=CONCLUIDO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].titulo", contains("Concluído")));
    }

    @Test
    void dadoAnunciosFinais_quandoEmpregadorFiltraCancelado_entaoSoCancelados() throws Exception {
        concluido("Concluído", dia("2026-10-12T17:40:00Z"));
        cancelado("Cancelado", dia("2026-10-20T12:00:00Z"));
        expirado("Expirado", dia("2026-10-03T12:00:00Z"));

        comoEmpregador("?status=CANCELADO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].titulo", contains("Cancelado")));
    }

    @Test
    void dadoAnunciosFinais_quandoEmpregadorFiltraExpirado_entaoSoExpirados() throws Exception {
        concluido("Concluído", dia("2026-10-12T17:40:00Z"));
        cancelado("Cancelado", dia("2026-10-20T12:00:00Z"));
        expirado("Expirado", dia("2026-10-03T12:00:00Z"));

        comoEmpregador("?status=EXPIRADO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].titulo", contains("Expirado")))
                .andExpect(jsonPath("$.content[0].outraParte", nullValue()))
                .andExpect(jsonPath("$.content[0].avaliacao", nullValue()));
    }

    // ---------- Prestador ----------

    @Test
    void dadoCandidaturasEmAnunciosFinais_quandoPrestadorFiltraPorStatus_entaoSoOsDele() throws Exception {
        Anuncio feito = concluido("Feito", dia("2026-10-12T17:40:00Z"));
        Anuncio cancelado = cancelado("Cancelado", dia("2026-10-20T12:00:00Z"));
        expirado("Sem candidatura dele", dia("2026-10-03T12:00:00Z"));
        candidatura(feito, prestador, true);
        candidatura(cancelado, prestador, false);

        comoPrestador("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].titulo", contains("Cancelado", "Feito")));
        comoPrestador("?status=CONCLUIDO")
                .andExpect(jsonPath("$.content[*].titulo", contains("Feito")));
        comoPrestador("?status=CANCELADO")
                .andExpect(jsonPath("$.content[*].titulo", contains("Cancelado")));
        comoPrestador("?status=EXPIRADO")
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void dadoPrestadorComDuasCandidaturasNoMesmoAnuncio_quandoListar_entaoUmItemSo() throws Exception {
        Anuncio cancelado = cancelado("Cancelado", dia("2026-10-20T12:00:00Z"));
        candidatura(cancelado, prestador, false);
        candidatura(cancelado, prestador, true);

        comoPrestador("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos", is(1)))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void dadoPrestadorNaoEscolhido_quandoListar_entaoSemContatoSemAvaliacaoEComNomeAbreviado() throws Exception {
        Anuncio feito = concluido("Feito por outro", dia("2026-10-12T17:40:00Z"));
        candidatura(feito, prestador, false);

        comoPrestador("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].outraParte", is("Maria S.")))
                .andExpect(jsonPath("$.content[0].avaliacao", nullValue()))
                .andExpect(jsonPath("$.content[0].telefone").doesNotExist())
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].endereco").doesNotExist())
                .andExpect(jsonPath("$.content[0].enderecoCompleto").doesNotExist())
                .andExpect(jsonPath("$.content[0].whatsapp").doesNotExist());
    }

    @Test
    void dadoPrestadorEscolhidoEmServicoCancelado_quandoListar_entaoNomeCompletoDoEmpregador() throws Exception {
        Anuncio cancelado = cancelado("Cancelado", dia("2026-10-20T12:00:00Z"));
        candidatura(cancelado, prestador, true);

        comoPrestador("")
                .andExpect(jsonPath("$.content[0].outraParte", is("Maria da Silva")))
                .andExpect(jsonPath("$.content[0].avaliacao", nullValue()));
    }

    // ---------- Período ----------

    @Test
    void dadoAnunciosEmDatasDiferentes_quandoFiltraPeriodo_entaoIncluiPrimeiroEUltimoDia() throws Exception {
        concluido("Antes", dia("2026-09-30T20:00:00Z"));
        concluido("Primeiro dia", dia("2026-10-01T03:00:00Z"));
        concluido("Meio", dia("2026-10-15T12:00:00Z"));
        concluido("Último dia", dia("2026-10-31T23:00:00Z"));
        concluido("Depois", dia("2026-11-01T03:00:00Z"));

        comoEmpregador("?dataInicio=2026-10-01&dataFim=2026-10-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].titulo", contains("Último dia", "Meio", "Primeiro dia")));
    }

    @Test
    void dadoDesfechoAs2330DeBrasilia_quandoFiltraOMesmoDia_entaoEntraMesmoJaSendoODiaSeguinteEmUtc()
            throws Exception {
        concluido("Noite", dia("2026-11-01T02:30:00Z")); // 31/10 23:30 em Brasília

        comoEmpregador("?dataInicio=2026-10-31&dataFim=2026-10-31")
                .andExpect(jsonPath("$.content[*].titulo", contains("Noite")));
        comoEmpregador("?dataInicio=2026-11-01&dataFim=2026-11-01")
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void dadoSoUmaPontaDoPeriodo_quandoListar_entaoOutraFicaSemLimite() throws Exception {
        concluido("Antigo", dia("2026-08-10T12:00:00Z"));
        concluido("Novo", dia("2026-10-10T12:00:00Z"));

        comoEmpregador("?dataInicio=2026-10-01")
                .andExpect(jsonPath("$.content[*].titulo", contains("Novo")));
        comoEmpregador("?dataFim=2026-09-01")
                .andExpect(jsonPath("$.content[*].titulo", contains("Antigo")));
    }

    @Test
    void dadoPeriodoSemAnuncios_quandoListar_entao200ComListaVazia() throws Exception {
        concluido("Fora", dia("2026-10-10T12:00:00Z"));

        comoEmpregador("?dataInicio=2025-01-01&dataFim=2025-01-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElementos", is(0)))
                .andExpect(jsonPath("$.totalPaginas", is(0)));
    }

    // ---------- Contraparte e avaliação ----------

    @Test
    void dadoServicoConcluidoComNotaRecebidaEJaAvaliado_quandoEmpregadorListar_entaoNotaEPodeAvaliarFalso()
            throws Exception {
        Anuncio feito = concluido("Feito", Instant.now().minus(Duration.ofDays(1)));
        candidatura(feito, prestador, true);
        avaliar(feito, prestador, empregador, PapelUsuario.EMPREGADOR, 5);
        avaliar(feito, empregador, prestador, PapelUsuario.PRESTADOR, 3);

        comoEmpregador("")
                .andExpect(jsonPath("$.content[0].outraParte", is("Rafael Souza Lima")))
                .andExpect(jsonPath("$.content[0].avaliacao.notaRecebida", is(5)))
                .andExpect(jsonPath("$.content[0].avaliacao.podeAvaliar", is(false)));
        comoPrestador("")
                .andExpect(jsonPath("$.content[0].outraParte", is("Maria da Silva")))
                .andExpect(jsonPath("$.content[0].avaliacao.notaRecebida", is(3)))
                .andExpect(jsonPath("$.content[0].avaliacao.podeAvaliar", is(false)));
    }

    @Test
    void dadoServicoConcluidoHaUmDiaSemAvaliacao_quandoListar_entaoPodeAvaliarSemNota() throws Exception {
        Anuncio feito = concluido("Feito", Instant.now().minus(Duration.ofDays(1)));
        candidatura(feito, prestador, true);

        comoEmpregador("")
                .andExpect(jsonPath("$.content[0].avaliacao.notaRecebida", nullValue()))
                .andExpect(jsonPath("$.content[0].avaliacao.podeAvaliar", is(true)));
        comoPrestador("")
                .andExpect(jsonPath("$.content[0].avaliacao.podeAvaliar", is(true)));
    }

    @Test
    void dadoServicoConcluidoHaMaisDeSeteDias_quandoListar_entaoNaoPodeMaisAvaliar() throws Exception {
        Anuncio feito = concluido("Feito", Instant.now().minus(Duration.ofDays(8)));
        candidatura(feito, prestador, true);

        comoEmpregador("")
                .andExpect(jsonPath("$.content[0].avaliacao.podeAvaliar", is(false)));
    }

    @Test
    void dadoConcluidoSemEscolhido_quandoEmpregadorListar_entaoSemOutraParteESemAvaliacao() throws Exception {
        concluido("Sem candidatura escolhida", Instant.now().minus(Duration.ofDays(1)));

        comoEmpregador("")
                .andExpect(jsonPath("$.content[0].outraParte", nullValue()))
                .andExpect(jsonPath("$.content[0].avaliacao", nullValue()));
    }

    @Test
    void dadoContraparteComContaEncerrada_quandoListar_entaoContaEncerrada() throws Exception {
        Anuncio cancelado = cancelado("Cancelado", dia("2026-10-20T12:00:00Z"));
        candidatura(cancelado, prestador, true);
        jdbc.update("update usuario set anonimizado_em = now() where id = ?", prestador.getId());

        comoEmpregador("")
                .andExpect(jsonPath("$.content[0].outraParte", is("Conta encerrada")));
    }

    // ---------- Segurança ----------

    @Test
    void dadoAnunciosDeOutroEmpregador_quandoListar_entaoNaoAparecemNemComIdNaQuery() throws Exception {
        Usuario outro = cadastrar("Carlos Dias", "carlos@example.com", "11977776666", "39053344705",
                PapelUsuario.EMPREGADOR);
        concluido(outro.getId(), "Do outro", dia("2026-10-12T17:40:00Z"));
        concluido("Meu", dia("2026-10-13T17:40:00Z"));

        comoEmpregador("?usuarioId=" + outro.getId() + "&contratanteId=" + outro.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].titulo", contains("Meu")));
    }

    @Test
    void dadoCandidaturaDeOutroPrestador_quandoPrestadorListar_entaoNaoVeOAnuncio() throws Exception {
        Usuario outro = cadastrar("Paulo Reis", "paulo@example.com", "11966665555", "86288366757",
                PapelUsuario.PRESTADOR);
        candidatura(concluido("Do outro prestador", dia("2026-10-12T17:40:00Z")), outro, true);

        comoPrestador("?prestadorId=" + outro.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void dadoMesmoUsuarioComPapelAtivoTrocado_quandoListarComNovoToken_entaoVisaoMuda() throws Exception {
        cancelado("Meu anúncio", dia("2026-10-20T12:00:00Z"));
        Usuario outro = cadastrar("Carlos Dias", "carlos@example.com", "11977776666", "39053344705",
                PapelUsuario.EMPREGADOR);
        Anuncio dele = cancelado2(outro, "Anúncio dele");
        candidatura(dele, empregador, false);

        comoEmpregador("").andExpect(jsonPath("$.content[*].titulo", contains("Meu anúncio")));

        jdbc.update("update usuario set papel_ativo = 'PRESTADOR' where id = ?", empregador.getId());
        String tokenComoPrestador = logar(EMAIL_EMPREGADOR);

        mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComoPrestador))
                .andExpect(jsonPath("$.content[*].titulo", contains("Anúncio dele")));
    }

    private Anuncio cancelado2(Usuario dono, String titulo) {
        return encerrado(dono.getId(), titulo, CausaEncerramento.CANCELAMENTO, dia("2026-10-21T12:00:00Z"));
    }

    @Test
    void dadoRequisicaoSemToken_quandoListar_entao401() throws Exception {
        mockMvc.perform(get(ROTA)).andExpect(status().isUnauthorized());
    }

    // ---------- Validação ----------

    @Test
    void dadoDataInicialPosteriorAFinal_quandoListar_entao400ValidacaoFalhou() throws Exception {
        comoEmpregador("?dataInicio=2026-10-31&dataFim=2026-10-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoMesmoDiaNasDuasDatas_quandoListar_entaoAceita() throws Exception {
        comoEmpregador("?dataInicio=2026-10-31&dataFim=2026-10-31").andExpect(status().isOk());
    }

    @Test
    void dadoStatusOuDataInvalidos_quandoListar_entao400ValidacaoFalhou() throws Exception {
        comoEmpregador("?status=PUBLICADO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        comoEmpregador("?dataInicio=31/10/2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
    }

    @Test
    void dadoPaginacaoInvalida_quandoListar_entao400ValidacaoFalhou() throws Exception {
        comoEmpregador("?tamanho=51").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("VALIDACAO_FALHOU")));
        comoEmpregador("?tamanho=0").andExpect(status().isBadRequest());
        comoEmpregador("?pagina=-1").andExpect(status().isBadRequest());
    }

    // ---------- Paginação ----------

    @Test
    void dadoVariosAnuncios_quandoPagina_entaoDivideEmPaginasNaOrdemDoDesfecho() throws Exception {
        concluido("A", dia("2026-10-01T12:00:00Z"));
        concluido("B", dia("2026-10-02T12:00:00Z"));
        concluido("C", dia("2026-10-03T12:00:00Z"));

        comoEmpregador("?tamanho=2&pagina=0")
                .andExpect(jsonPath("$.content[*].titulo", contains("C", "B")))
                .andExpect(jsonPath("$.pagina", is(0)))
                .andExpect(jsonPath("$.tamanho", is(2)))
                .andExpect(jsonPath("$.totalElementos", is(3)))
                .andExpect(jsonPath("$.totalPaginas", is(2)));
        comoEmpregador("?tamanho=2&pagina=1")
                .andExpect(jsonPath("$.content[*].titulo", contains("A")))
                .andExpect(jsonPath("$.pagina", is(1)));
    }
}
