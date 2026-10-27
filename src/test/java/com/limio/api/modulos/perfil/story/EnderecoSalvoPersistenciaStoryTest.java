package com.limio.api.modulos.perfil.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorRepository;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoRepository;
import com.limio.api.modulos.perfil.enums.Uf;

/**
 * Fumaça da persistência de {@code endereco_salvo} (TICKET-0050, Parte 2): o Flyway sobe, o
 * {@code ddl-auto=validate} aceita a entidade contra a migration, os finders ignoram removidos e
 * o banco (CHECKs + índice único parcial) segura as invariantes sozinho. Sem HTTP: o contrato
 * entra nas partes seguintes.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EnderecoSalvoPersistenciaStoryTest {

    @Autowired
    private EnderecoSalvoRepository enderecos;

    @Autowired
    private PerfilEmpregadorRepository perfis;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transacao;

    @BeforeEach
    @AfterEach
    void limparBase() {
        // endereco_salvo antes de perfil_empregador (FK); no @AfterEach pra não travar a limpeza de outras stories.
        jdbc.execute("delete from endereco_salvo");
        jdbc.execute("delete from perfil_empregador");
    }

    @Test
    void dadoEnderecoValido_quandoSalva_entaoGravaTodosOsCamposInclusiveCoordenadas() {
        UUID perfilId = novoPerfil();

        EnderecoSalvo salvo = enderecos.saveAndFlush(endereco(perfilId, "Casa", false));

        EnderecoSalvo lido = enderecos.findById(salvo.getId()).orElseThrow();
        assertThat(lido.getPerfilEmpregadorId()).isEqualTo(perfilId);
        assertThat(lido.getApelido()).isEqualTo("Casa");
        assertThat(lido.getCep()).isEqualTo("01310100");
        assertThat(lido.getRua()).isEqualTo("Avenida Paulista");
        assertThat(lido.getNumero()).isEqualTo("1000");
        assertThat(lido.getComplemento()).isEqualTo("Apto 12");
        assertThat(lido.getUf()).isEqualTo(Uf.SP);
        assertThat(lido.getLatitude()).isEqualTo(-23.5614);
        assertThat(lido.getLongitude()).isEqualTo(-46.6559);
        assertThat(lido.isPadrao()).isFalse();
        assertThat(lido.getRemovidoEm()).isNull();
        assertThat(lido.getCriadoEm()).isNotNull();
        assertThat(jdbc.queryForObject("select uf from endereco_salvo where id = ?", String.class, salvo.getId()))
                .isEqualTo("SP");
    }

    @Test
    void dadoEnderecoRemovido_quandoLista_entaoFicaForaMasPermaneceNoBanco() {
        UUID perfilId = novoPerfil();
        EnderecoSalvo ativo = enderecos.saveAndFlush(endereco(perfilId, "Casa", false));
        EnderecoSalvo removido = endereco(perfilId, "Antigo", false);
        removido.remover(Instant.now());
        removido = enderecos.saveAndFlush(removido);

        List<EnderecoSalvo> lista = enderecos
                .findAllByPerfilEmpregadorIdAndRemovidoEmIsNullOrderByPadraoDescApelidoAsc(perfilId);

        assertThat(lista).extracting(EnderecoSalvo::getId).containsExactly(ativo.getId());
        assertThat(enderecos.findByIdAndPerfilEmpregadorIdAndRemovidoEmIsNull(removido.getId(), perfilId)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from endereco_salvo", Integer.class)).isEqualTo(2);
    }

    @Test
    void dadoEnderecoDeOutroPerfil_quandoBuscaPorId_entaoNaoEncontra() {
        UUID dono = novoPerfil();
        UUID outro = novoPerfil();
        EnderecoSalvo endereco = enderecos.saveAndFlush(endereco(dono, "Casa", false));

        assertThat(enderecos.findByIdAndPerfilEmpregadorIdAndRemovidoEmIsNull(endereco.getId(), outro)).isEmpty();
        assertThat(enderecos.findByIdAndPerfilEmpregadorIdAndRemovidoEmIsNull(endereco.getId(), dono)).isPresent();
    }

    @Test
    void dadoPadraoEApelidos_quandoLista_entaoPadraoVemPrimeiroDepoisPorApelido() {
        UUID perfilId = novoPerfil();
        enderecos.saveAndFlush(endereco(perfilId, "Trabalho", false));
        enderecos.saveAndFlush(endereco(perfilId, "Casa", false));
        enderecos.saveAndFlush(endereco(perfilId, "Sítio", true));

        assertThat(enderecos.findAllByPerfilEmpregadorIdAndRemovidoEmIsNullOrderByPadraoDescApelidoAsc(perfilId))
                .extracting(EnderecoSalvo::getApelido)
                .containsExactly("Sítio", "Casa", "Trabalho");
    }

    @Test
    void dadoDoisPadroesAtivosNoMesmoPerfil_quandoSalva_entaoOIndiceUnicoParcialRejeita() {
        UUID perfilId = novoPerfil();
        enderecos.saveAndFlush(endereco(perfilId, "Casa", true));

        assertThatThrownBy(() -> enderecos.saveAndFlush(endereco(perfilId, "Trabalho", true)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_endereco_salvo_padrao");
    }

    @Test
    void dadoPadraoRemovidoOuDeOutroPerfil_quandoMarcaOutroPadrao_entaoOIndiceNaoBloqueia() {
        UUID perfilA = novoPerfil();
        UUID perfilB = novoPerfil();
        EnderecoSalvo antigo = endereco(perfilA, "Antigo", true);
        enderecos.saveAndFlush(antigo);
        jdbc.update("update endereco_salvo set removido_em = now() where id = ?", antigo.getId());

        enderecos.saveAndFlush(endereco(perfilA, "Casa", true));
        enderecos.saveAndFlush(endereco(perfilB, "Casa", true));

        assertThat(jdbc.queryForObject("select count(*) from endereco_salvo where is_padrao", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void dadoPadraoAtivo_quandoDesmarcaPadrao_entaoSoOAtivoDoPerfilDeixaDeSer() {
        UUID perfilA = novoPerfil();
        UUID perfilB = novoPerfil();
        EnderecoSalvo deA = enderecos.saveAndFlush(endereco(perfilA, "Casa", true));
        EnderecoSalvo deB = enderecos.saveAndFlush(endereco(perfilB, "Casa", true));

        int afetados = transacao.execute(status -> enderecos.desmarcarPadrao(perfilA, Instant.now()));

        assertThat(afetados).isEqualTo(1);
        assertThat(enderecos.findById(deA.getId()).orElseThrow().isPadrao()).isFalse();
        assertThat(enderecos.findById(deB.getId()).orElseThrow().isPadrao()).isTrue();
    }

    @Test
    void dadoValoresInvalidos_quandoInsereDireto_entaoOsChecksDoBancoRejeitam() {
        UUID perfilId = novoPerfil();

        assertThat(inserirDireto(perfilId, "1234567", "SP", -23.0, -46.0)).as("cep com 7 dígitos").isFalse();
        assertThat(inserirDireto(perfilId, "0131010A", "SP", -23.0, -46.0)).as("cep com letra").isFalse();
        assertThat(inserirDireto(perfilId, "01310100", "XX", -23.0, -46.0)).as("uf inexistente").isFalse();
        assertThat(inserirDireto(perfilId, "01310100", "SP", 91.0, -46.0)).as("latitude fora da faixa").isFalse();
        assertThat(inserirDireto(perfilId, "01310100", "SP", -23.0, 181.0)).as("longitude fora da faixa").isFalse();
        assertThat(inserirDireto(perfilId, "01310100", "SP", -23.0, -46.0)).as("válido").isTrue();
    }

    @Test
    void dadoPerfilComEnderecos_quandoApagaOPerfil_entaoAFkImpede() {
        UUID perfilId = novoPerfil();
        enderecos.saveAndFlush(endereco(perfilId, "Casa", false));

        assertThatThrownBy(() -> jdbc.update("delete from perfil_empregador where id = ?", perfilId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID novoPerfil() {
        return perfis.saveAndFlush(PerfilEmpregador.builder()
                .usuarioId(UUID.randomUUID())
                .nomeExibicao("Maria")
                .build()).getId();
    }

    private EnderecoSalvo endereco(UUID perfilId, String apelido, boolean padrao) {
        return EnderecoSalvo.builder()
                .perfilEmpregadorId(perfilId)
                .apelido(apelido)
                .cep("01310100")
                .rua("Avenida Paulista")
                .numero("1000")
                .complemento("Apto 12")
                .bairroRegiao("Bela Vista")
                .cidade("São Paulo")
                .uf(Uf.SP)
                .latitude(-23.5614)
                .longitude(-46.6559)
                .padrao(padrao)
                .build();
    }

    private boolean inserirDireto(UUID perfilId, String cep, String uf, double latitude, double longitude) {
        try {
            jdbc.update("""
                    insert into endereco_salvo (perfil_empregador_id, apelido, cep, rua, numero,
                                                bairro_regiao, cidade, uf, latitude, longitude)
                    values (?, 'Teste', ?, 'Rua A', '1', 'Centro', 'São Paulo', ?, ?, ?)
                    """, perfilId, cep, uf, latitude, longitude);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
