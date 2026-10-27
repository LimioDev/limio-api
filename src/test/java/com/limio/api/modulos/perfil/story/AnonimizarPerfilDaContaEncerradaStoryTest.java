package com.limio.api.modulos.perfil.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;

import tools.jackson.databind.ObjectMapper;

/**
 * BDD ponta a ponta (controller {@code auth} -> listener {@code perfil} -> banco real): reação do
 * {@code perfil} ao encerramento de conta (pendência do TICKET-0034, resolvida no TICKET-0047). O
 * perfil de prestador e a foto são semeados por SQL — salvar o perfil pela API chamaria a geocodificação
 * externa, e o storage não existe nos testes.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AnonimizarPerfilDaContaEncerradaStoryTest {

    private static final String EMAIL = "maria@example.com";
    private static final String SENHA = "senhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limparBase() {
        jdbc.execute("delete from janela_atendimento");
        jdbc.execute("delete from prestador_categoria");
        jdbc.execute("delete from perfil_prestador");
        jdbc.execute("delete from perfil_empregador");
        jdbc.execute("delete from midia");
        jdbc.execute("delete from cpf_bloqueado");
        jdbc.execute("TRUNCATE TABLE usuario CASCADE");
    }

    private String cadastrarELogar() throws Exception {
        mockMvc.perform(post("/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "nomeCompleto", "Maria da Silva", "email", EMAIL, "telefone", "11999998888",
                        "senha", SENHA, "cpf", "52998224725", "dataNascimento", "1990-01-01",
                        "cidadeUf", "São Paulo/SP"))))
                .andExpect(status().isCreated());
        String corpo = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "senha", SENHA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("token").asString();
    }

    private UUID usuarioId() {
        return jdbc.queryForObject("select id from usuario where email = ?", UUID.class, EMAIL);
    }

    private void semearPrestadorEFoto(UUID usuarioId) {
        UUID perfilId = jdbc.queryForObject("""
                insert into perfil_prestador (usuario_id, raio_atendimento_km, municipio_base, uf_base,
                    latitude_base, longitude_base, completo)
                values (?, 10, 'Campinas', 'SP', -22.9, -47.06, true) returning id
                """, UUID.class, usuarioId);
        jdbc.update("insert into janela_atendimento (perfil_prestador_id, dia_semana, hora_inicio, hora_fim) "
                + "values (?, 'SEGUNDA', '08:00', '12:00')", perfilId);
        jdbc.update("insert into prestador_categoria (perfil_prestador_id, categoria_id) "
                + "select ?, id from categoria limit 1", perfilId);
        jdbc.update("insert into midia (usuario_id, tipo, url_s3, mime, largura, altura, bytes, exif_removido) "
                + "values (?, 'FOTO_PERFIL', 'fotos-perfil/teste.jpg', 'image/jpeg', 10, 10, 100, true)", usuarioId);
    }

    @Test
    void dadoContaComPerfisEFoto_quandoEncerra_entaoNomeAnonimizadoPrestadorApagadoEFotoExpurgada() throws Exception {
        String token = cadastrarELogar();
        mockMvc.perform(get("/conta/perfil-empregador").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        UUID usuarioId = usuarioId();
        semearPrestadorEFoto(usuarioId);

        mockMvc.perform(post("/auth/encerrar-conta")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("confirmacao", true))))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select nome_exibicao from perfil_empregador where usuario_id = ?",
                String.class, usuarioId)).isEqualTo(PerfilEmpregador.NOME_CONTA_ENCERRADA);
        assertThat(jdbc.queryForObject("select count(*) from perfil_prestador where usuario_id = ?",
                Integer.class, usuarioId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from janela_atendimento", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from prestador_categoria", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from midia where usuario_id = ? and expurgo_em is null",
                Integer.class, usuarioId)).isZero();
    }
}
