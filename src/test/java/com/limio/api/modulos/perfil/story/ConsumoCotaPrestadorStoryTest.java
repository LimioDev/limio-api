package com.limio.api.modulos.perfil.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.portas.ConsumoCotaPrestador;

/**
 * BDD do consumo da cota mensal (ETI-69), pela porta de escrita {@link ConsumoCotaPrestador}, no banco real. A porta
 * exige transação, então cada chamada roda num {@link TransactionTemplate} (como o usecase de escolha fará). O foco
 * é a concorrência: o saldo é protegido por {@code COTA_PRESTADOR} mesmo quando a linha do mês ainda nem existe.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ConsumoCotaPrestadorStoryTest {

    /** 29/10/2026, meio-dia em São Paulo. */
    private static final Instant OUTUBRO = Instant.parse("2026-10-29T15:00:00Z");
    private static final int TETO_FREE = 4;

    @Autowired
    private ConsumoCotaPrestador consumoCota;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID prestadorId;
    private UUID perfilId;

    @BeforeEach
    void prestadorComPerfil() {
        prestadorId = UUID.randomUUID();
        perfilId = UUID.randomUUID();
        jdbc.update("""
                insert into perfil_prestador (id, usuario_id, raio_atendimento_km, municipio_base, uf_base,
                    latitude_base, longitude_base, completo)
                values (?, ?, 10, 'Campinas', 'SP', -22.9, -47.06, true)""", perfilId, prestadorId);
    }

    @AfterEach
    void limpar() {
        jdbc.update("delete from cota_mensal where perfil_prestador_id = ?", perfilId);
        jdbc.update("delete from perfil_prestador where id = ?", perfilId);
    }

    private boolean consumir(Instant quando) {
        return transacao.execute(status -> consumoCota.consumirEscolha(prestadorId, quando));
    }

    private int consumidasEm(String anoMes) {
        return jdbc.queryForObject("select escolhas_consumidas from cota_mensal where perfil_prestador_id = ? "
                + "and ano_mes = ?", Integer.class, perfilId, anoMes);
    }

    private int linhas() {
        return jdbc.queryForObject("select count(*) from cota_mensal where perfil_prestador_id = ?", Integer.class,
                perfilId);
    }

    @Test
    void dadoMesSemLinha_quandoConsome_entaoCriaALinhaComUmaEscolhaETetoFree() {
        assertThat(consumir(OUTUBRO)).isTrue();

        assertThat(linhas()).isEqualTo(1);
        assertThat(consumidasEm("2026-10")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select teto_aplicado from cota_mensal where perfil_prestador_id = ?",
                Integer.class, perfilId)).isEqualTo(TETO_FREE);
    }

    @Test
    void dadasQuatroEscolhas_quandoConsomeAQuinta_entaoRecusaESaldoFicaEmQuatro() {
        for (int i = 0; i < TETO_FREE; i++) {
            assertThat(consumir(OUTUBRO)).isTrue();
        }

        assertThat(consumir(OUTUBRO)).isFalse();

        assertThat(consumidasEm("2026-10")).isEqualTo(TETO_FREE);
    }

    @Test
    void dadaCotaEsgotadaEmOutubro_quandoConsomeEmNovembro_entaoMesNovoComecaDoZero() {
        for (int i = 0; i < TETO_FREE; i++) {
            consumir(OUTUBRO);
        }

        // 01/11 00:00 em São Paulo
        assertThat(consumir(Instant.parse("2026-11-01T03:00:00Z"))).isTrue();

        assertThat(consumidasEm("2026-11")).isEqualTo(1);
        assertThat(consumidasEm("2026-10")).isEqualTo(TETO_FREE);
    }

    @Test
    void dadoRollbackDaTransacao_quandoConsome_entaoNadaFicaGravadoENemALinhaDoMes() {
        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
            assertThat(consumoCota.consumirEscolha(prestadorId, OUTUBRO)).isTrue();
            throw new IllegalStateException("falha depois do consumo");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(linhas()).isZero();
    }

    @Test
    void dadoSemTransacaoAberta_quandoConsome_entaoRecusaPorSerEscritaNaTransacaoDaEscolha() {
        assertThatThrownBy(() -> consumoCota.consumirEscolha(prestadorId, OUTUBRO))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(linhas()).isZero();
    }

    /**
     * Oito escolhas do mesmo Prestador disparadas juntas, SEM linha do mês ainda. Sem a trava, várias leriam "sem
     * linha", tentariam criar (unique estoura) ou leriam o mesmo saldo e gravariam por cima. Com ela entram em fila:
     * exatamente 4 consomem, 4 são recusadas, e o banco termina com uma linha e saldo 4.
     */
    @Test
    void dadasOitoEscolhasSimultaneasSemLinha_quandoConsomem_entaoSoQuatroPassamEASaldoFinalEQuatro()
            throws Exception {
        int disputantes = 8;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(disputantes);
        List<Future<Boolean>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < disputantes; i++) {
                resultados.add(executor.submit(() -> {
                    largada.await();
                    return consumir(OUTUBRO);
                }));
            }
            largada.countDown();

            int consumiram = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get(30, TimeUnit.SECONDS)) {
                    consumiram++;
                }
            }

            assertThat(consumiram).isEqualTo(TETO_FREE);
        } finally {
            largada.countDown();
            executor.shutdownNow();
        }

        assertThat(linhas()).isEqualTo(1);
        assertThat(consumidasEm("2026-10")).isEqualTo(TETO_FREE);
    }
}
