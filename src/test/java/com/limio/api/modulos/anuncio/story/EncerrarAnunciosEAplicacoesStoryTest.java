package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.auditoria.LogAuditoria;
import com.limio.api.comum.auditoria.LogAuditoriaRepository;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioRepository;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoRepository;
import com.limio.api.modulos.auth.cpfbloqueado.CpfBloqueadoRepository;
import com.limio.api.modulos.auth.records.LoginRequest;
import com.limio.api.modulos.auth.records.LoginResponse;
import com.limio.api.modulos.auth.sessao.SessaoRepository;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller {@code auth} -> listener {@code anuncio} ->
 * banco real) do TICKET-0049: reação de {@code anuncio} ao {@code
 * ContaEncerradaEvent}. Não existe usecase de "criar anúncio" ainda —
 * semeia as linhas direto pelo repository, igual outras stories fazem com
 * entity que não tem endpoint de criação (ex.: {@code CpfBloqueado} em
 * {@code EncerrarContaStoryTest}).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EncerrarAnunciosEAplicacoesStoryTest {

    private static final String SENHA = "senhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SessaoRepository sessaoRepository;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AplicacaoRepository aplicacaoRepository;

    @Autowired
    private ReagendamentoRepository reagendamentoRepository;

    @Autowired
    private LogAuditoriaRepository logAuditoriaRepository;

    @Autowired
    private CpfBloqueadoRepository cpfBloqueadoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void limparBase() {
        reagendamentoRepository.deleteAll();
        aplicacaoRepository.deleteAll();
        anuncioRepository.deleteAll();
        logAuditoriaRepository.deleteAll();
        sessaoRepository.deleteAll();
        cpfBloqueadoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @AfterEach
    void naoDeixarNadaPraTras() {
        limparBase();
    }

    private Usuario cadastrar(String email, String cpf) {
        return usuarioRepository.save(Usuario.builder()
                .nomeCompleto("Usuário Teste")
                .email(email)
                .telefone("11999998888")
                .senhaHash(passwordEncoder.encode(SENHA))
                .cpf(cpf)
                .dataNascimento(LocalDate.now().minusYears(25))
                .cidadeUf("São Paulo/SP")
                .papelAtivo(PapelUsuario.EMPREGADOR)
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

    private void encerrarConta(String token) throws Exception {
        mockMvc.perform(post("/auth/encerrar-conta")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confirmacao", true))))
                .andExpect(status().isOk());
    }

    private Anuncio anuncio(UUID contratanteId, StatusAnuncio status, Instant dataHoraPretendida) {
        return anuncioRepository.save(Anuncio.builder()
                .titulo("Faxina")
                .contratanteId(contratanteId)
                .descricao("Faxina pós-obra")
                .dataHoraPretendida(dataHoraPretendida)
                .status(status)
                .build());
    }

    private Aplicacao aplicacao(UUID anuncioId, UUID prestadorId, StatusAplicacao status) {
        return aplicacaoRepository.save(Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(prestadorId)
                .status(status)
                .dataAplicacao(Instant.now())
                .build());
    }

    @Test
    void dadoAnuncioAbertoSemCandidato_quandoContratanteEncerraConta_entaoAnuncioEncerraSemAviso() throws Exception {
        Usuario contratante = cadastrar("contratante1@example.com", "52998224725");
        Anuncio anuncio = anuncio(contratante.getId(), StatusAnuncio.PUBLICADO, Instant.now().plus(Duration.ofDays(10)));
        String token = logar(contratante.getEmail());

        encerrarConta(token);

        Anuncio atualizado = anuncioRepository.findById(anuncio.getId()).orElseThrow();
        assertThat(atualizado.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(atualizado.getEncerradoEm()).isNotNull();
        // só a auditoria de CONTA_ENCERRADA do próprio auth (TICKET-0034) — nada de anuncio aqui
        assertThat(logAuditoriaRepository.findAll())
                .extracting(LogAuditoria::getTipoAcao)
                .containsExactly(TipoAcaoAuditoria.CONTA_ENCERRADA);
    }

    @Test
    void dadoAnuncioComReagendamentoPendente_quandoContratanteEncerraConta_entaoReagendamentoCancelaSemEfeito()
            throws Exception {
        Usuario contratante = cadastrar("contratante2@example.com", "11144477735");
        Anuncio anuncio = anuncio(contratante.getId(), StatusAnuncio.PUBLICADO, Instant.now().plus(Duration.ofDays(10)));
        Reagendamento pendente = reagendamentoRepository.save(Reagendamento.builder()
                .anuncioId(anuncio.getId())
                .dataAnterior(anuncio.getDataHoraPretendida())
                .dataProposta(anuncio.getDataHoraPretendida().plus(Duration.ofDays(3)))
                .status(StatusReagendamento.PENDENTE)
                .build());
        String token = logar(contratante.getEmail());

        encerrarConta(token);

        Reagendamento atualizado = reagendamentoRepository.findById(pendente.getId()).orElseThrow();
        assertThat(atualizado.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
        assertThat(atualizado.getRespondidoEm()).isNotNull();
    }

    @Test
    void dadoCandidaturaEscolhidaComCombinadoDentroDe24h_quandoContratanteEncerraConta_entaoRegistraAuditoriaDeCombinadoCancelado()
            throws Exception {
        Usuario contratante = cadastrar("contratante3@example.com", "10433218100");
        Usuario prestador = cadastrar("prestador1@example.com", "47525534144");
        Anuncio anuncio = anuncio(contratante.getId(), StatusAnuncio.EM_ANDAMENTO, Instant.now().plus(Duration.ofHours(10)));
        aplicacao(anuncio.getId(), prestador.getId(), StatusAplicacao.ESCOLHIDA);
        String token = logar(contratante.getEmail());

        encerrarConta(token);

        Anuncio atualizado = anuncioRepository.findById(anuncio.getId()).orElseThrow();
        assertThat(atualizado.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        // duas entradas: CONTA_ENCERRADA (auth, TICKET-0034) + COMBINADO_CANCELADO (anuncio, este card)
        LogAuditoria entrada = logAuditoriaRepository.findAll().stream()
                .filter(log -> log.getTipoAcao() == TipoAcaoAuditoria.COMBINADO_CANCELADO)
                .findFirst().orElseThrow();
        assertThat(entrada.getEntidade()).isEqualTo("Anuncio");
        assertThat(entrada.getEntidadeId()).isEqualTo(anuncio.getId());
    }

    @Test
    void dadoCandidaturaEscolhidaDoPrestador_quandoPrestadorEncerraConta_entaoCandidaturaEAnuncioEncerram()
            throws Exception {
        Usuario contratante = cadastrar("contratante4@example.com", "08386379499");
        Usuario prestador = cadastrar("prestador2@example.com", "96001338914");
        Anuncio anuncio = anuncio(contratante.getId(), StatusAnuncio.EM_ANDAMENTO, Instant.now().plus(Duration.ofDays(5)));
        Aplicacao escolhida = aplicacao(anuncio.getId(), prestador.getId(), StatusAplicacao.ESCOLHIDA);
        String token = logar(prestador.getEmail());

        encerrarConta(token);

        Aplicacao aplicacaoAtualizada = aplicacaoRepository.findById(escolhida.getId()).orElseThrow();
        assertThat(aplicacaoAtualizada.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(aplicacaoAtualizada.getMotivoEncerramento()).isEqualTo("Cancelado por saída de conta");
        Anuncio anuncioAtualizado = anuncioRepository.findById(anuncio.getId()).orElseThrow();
        assertThat(anuncioAtualizado.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
    }

    @Test
    void dadoCandidaturaEnviadaNaoEscolhida_quandoPrestadorEncerraConta_entaoSoACandidaturaEncerra() throws Exception {
        Usuario contratante = cadastrar("contratante5@example.com", "16155940789");
        Usuario prestador = cadastrar("prestador3@example.com", "81618495950");
        Anuncio anuncio = anuncio(contratante.getId(), StatusAnuncio.PUBLICADO, Instant.now().plus(Duration.ofDays(10)));
        Aplicacao enviada = aplicacao(anuncio.getId(), prestador.getId(), StatusAplicacao.ENVIADA);
        String token = logar(prestador.getEmail());

        encerrarConta(token);

        Aplicacao aplicacaoAtualizada = aplicacaoRepository.findById(enviada.getId()).orElseThrow();
        assertThat(aplicacaoAtualizada.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        Anuncio anuncioInalterado = anuncioRepository.findById(anuncio.getId()).orElseThrow();
        assertThat(anuncioInalterado.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
    }
}
