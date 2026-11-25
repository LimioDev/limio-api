package com.limio.api.modulos.anuncio.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoRepository;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/**
 * Contrato do gancho que a conclusão por silêncio (ETI-98) usa (ETI-99): enquanto houver proposta de reagendamento
 * pendente, {@link ReagendamentoService#existePendente} é verdadeiro e o job NÃO pode concluir o serviço; em qualquer
 * estado final ela é falsa e a avaliação volta ao normal, com a data vigente que o anúncio tiver (a nova, se o aceite
 * a trocou). Contra Postgres real, para exercitar a consulta derivada de verdade. O job do ETI-98 deve checar esse
 * método depois da trava {@code ANUNCIO} e antes de {@code Anuncio.validar}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReagendamentoPendenteGanchoStoryTest {

    @Autowired
    private ReagendamentoService reagendamentoService;

    @Autowired
    private ReagendamentoRepository reagendamentoRepository;

    @BeforeEach
    @AfterEach
    void limparBase() {
        reagendamentoRepository.deleteAll();
    }

    private Reagendamento proposta(UUID anuncioId, StatusReagendamento status, boolean aposDataVigente) {
        return reagendamentoRepository.save(Reagendamento.builder()
                .anuncioId(anuncioId)
                .dataAnterior(Instant.now())
                .dataProposta(Instant.now().plus(Duration.ofDays(2)))
                .justificativa("Imprevisto")
                .status(status)
                .proponenteId(UUID.randomUUID())
                .expiraEm(Instant.now().plus(Duration.ofHours(24)))
                .propostaAposDataVigente(aposDataVigente)
                .build());
    }

    @Test
    void dadaPropostaPendente_quandoConsultaOGancho_entaoHaPendente() {
        UUID anuncioId = UUID.randomUUID();
        proposta(anuncioId, StatusReagendamento.PENDENTE, false);

        assertThat(reagendamentoService.existePendente(anuncioId)).isTrue();
    }

    @Test
    void dadaPropostaEmQualquerEstadoFinal_quandoConsultaOGancho_entaoNaoHaPendente() {
        for (StatusReagendamento status : StatusReagendamento.values()) {
            if (status == StatusReagendamento.PENDENTE) {
                continue;
            }
            UUID anuncioId = UUID.randomUUID();
            proposta(anuncioId, status, false);

            assertThat(reagendamentoService.existePendente(anuncioId))
                    .as("existePendente com a proposta %s", status).isFalse();
        }
    }

    @Test
    void dadoAnuncioSemPropostas_quandoConsultaOGancho_entaoNaoHaPendente() {
        assertThat(reagendamentoService.existePendente(UUID.randomUUID())).isFalse();
    }

    @Test
    void dadaPropostaPendenteDeOutroAnuncio_quandoConsultaOGancho_entaoNaoAfetaEste() {
        proposta(UUID.randomUUID(), StatusReagendamento.PENDENTE, false);

        assertThat(reagendamentoService.existePendente(UUID.randomUUID())).isFalse();
    }

    @Test
    void dadaPropostaAposADataVigenteEmQualquerDesfecho_quandoConsulta_entaoContaComoJaUsada() {
        UUID anuncioId = UUID.randomUUID();
        proposta(anuncioId, StatusReagendamento.RECUSADO, true);

        assertThat(reagendamentoService.existePropostaAposDataVigente(anuncioId)).isTrue();
        assertThat(reagendamentoService.existePropostaAposDataVigente(UUID.randomUUID())).isFalse();
    }

    @Test
    void dadosAceitosEOutrosDesfechos_quandoContaOsAceitos_entaoSoContaOsAceitos() {
        UUID anuncioId = UUID.randomUUID();
        proposta(anuncioId, StatusReagendamento.ACEITO, false);
        proposta(anuncioId, StatusReagendamento.ACEITO, false);
        proposta(anuncioId, StatusReagendamento.RECUSADO, false);
        proposta(anuncioId, StatusReagendamento.EXPIRADO, false);
        proposta(anuncioId, StatusReagendamento.CANCELADO, false);

        assertThat(reagendamentoService.contarAceitos(anuncioId)).isEqualTo(2);
    }
}
