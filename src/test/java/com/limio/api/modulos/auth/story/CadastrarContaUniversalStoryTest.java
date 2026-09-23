package com.limio.api.modulos.auth.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.limio.api.TestcontainersConfiguration;

import tools.jackson.databind.ObjectMapper;
import com.limio.api.modulos.auth.CpfBloqueado;
import com.limio.api.modulos.auth.CpfBloqueadoRepository;
import com.limio.api.modulos.auth.Usuario;
import com.limio.api.modulos.auth.UsuarioRepository;
import com.limio.api.modulos.auth.enums.PapelUsuario;
import com.limio.api.modulos.auth.enums.StatusConta;
import com.limio.api.modulos.auth.records.CadastroRequest;

/**
 * BDD ponta a ponta (controller -> banco real) do UC01 — cadastrar conta
 * universal. Sobe Spring context + Testcontainers Postgres.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CadastrarContaUniversalStoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CpfBloqueadoRepository cpfBloqueadoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void limparBase() {
        usuarioRepository.deleteAll();
        cpfBloqueadoRepository.deleteAll();
    }

    private CadastroRequest requestValido(String cpf, String email) {
        return new CadastroRequest(
                "Maria da Silva",
                email,
                "11999998888",
                "senhaForte123",
                cpf,
                LocalDate.now().minusYears(25),
                "São Paulo/SP");
    }

    @Test
    void dadoVisitanteComDadosValidos_quandoCadastra_entaoContaCriadaComPapelContratante() throws Exception {
        CadastroRequest request = requestValido("52998224725", "maria@example.com");

        mockMvc.perform(post("/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papelAtivo", is("CONTRATANTE")))
                .andExpect(jsonPath("$.email", is("maria@example.com")));

        Usuario salvo = usuarioRepository.findAll().get(0);
        assertThat(salvo.getPapelAtivo()).isEqualTo(PapelUsuario.CONTRATANTE);
        assertThat(salvo.getStatusConta()).isEqualTo(StatusConta.ATIVA);
        assertThat(passwordEncoder.matches("senhaForte123", salvo.getSenhaHash())).isTrue();
    }

    @Test
    void dadoVisitanteMenorDeIdade_quandoCadastra_entaoRecusaSemCriarConta() throws Exception {
        CadastroRequest request = new CadastroRequest(
                "João Menor",
                "joao@example.com",
                "11999998888",
                "senhaForte123",
                "52998224725",
                LocalDate.now().minusYears(17),
                "São Paulo/SP");

        mockMvc.perform(post("/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("CADASTRO_IDADE_MINIMA")));

        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    void dadoCpfComDigitoVerificadorInvalido_quandoCadastra_entaoRecusa() throws Exception {
        CadastroRequest request = requestValido("52998224726", "maria@example.com");

        mockMvc.perform(post("/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("CADASTRO_CPF_INVALIDO")));
    }

    @Test
    void dadoEmailJaCadastrado_quandoCadastraComCpfDiferente_entaoRecusaComErroGenerico() throws Exception {
        usuarioRepository.save(Usuario.builder()
                .nomeCompleto("Existente")
                .email("maria@example.com")
                .telefone("11988887777")
                .senhaHash(passwordEncoder.encode("outrasenha"))
                .cpf("11144477735")
                .dataNascimento(LocalDate.now().minusYears(30))
                .cidadeUf("Rio de Janeiro/RJ")
                .papelAtivo(PapelUsuario.CONTRATANTE)
                .statusConta(StatusConta.ATIVA)
                .build());

        CadastroRequest request = requestValido("52998224725", "maria@example.com");

        mockMvc.perform(post("/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CADASTRO_DADOS_INDISPONIVEIS")));
    }

    @Test
    void dadoCpfExcluidoHaMenosDe12Meses_quandoCadastra_entaoBloqueiaComErroGenerico() throws Exception {
        String cpfExcluido = "52998224725";
        CpfBloqueado bloqueio = new CpfBloqueado();
        bloqueio.setCpf(cpfExcluido);
        cpfBloqueadoRepository.save(bloqueio);

        CadastroRequest request = requestValido(cpfExcluido, "novo@example.com");

        mockMvc.perform(post("/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CADASTRO_DADOS_INDISPONIVEIS")));

        assertThat(usuarioRepository.count()).isZero();
    }
}
