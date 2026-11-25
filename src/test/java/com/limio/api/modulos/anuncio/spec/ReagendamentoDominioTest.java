package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;

/** Ciclo de vida da proposta de reagendamento (ETI-99): as transições e o prazo vivem no domínio. */
class ReagendamentoDominioTest {

    private static final Instant AGORA = Instant.parse("2026-11-11T15:00:00Z");
    private static final UUID PROPONENTE = UUID.randomUUID();

    private static Reagendamento pendente(boolean aposDataVigente) {
        return Reagendamento.propor(UUID.randomUUID(), PROPONENTE, AGORA.plus(Duration.ofDays(3)),
                AGORA.plus(Duration.ofDays(5)), "Imprevisto", AGORA.plus(Duration.ofHours(24)), aposDataVigente);
    }

    @Test
    void proporCriaPendenteComOsDadosInformadosESemResposta() {
        Reagendamento proposta = pendente(true);

        assertThat(proposta.getStatus()).isEqualTo(StatusReagendamento.PENDENTE);
        assertThat(proposta.getProponenteId()).isEqualTo(PROPONENTE);
        assertThat(proposta.getJustificativa()).isEqualTo("Imprevisto");
        assertThat(proposta.getExpiraEm()).isEqualTo(AGORA.plus(Duration.ofHours(24)));
        assertThat(proposta.isPropostaAposDataVigente()).isTrue();
        assertThat(proposta.getRespondidoEm()).isNull();
        assertThat(pendente(false).isPropostaAposDataVigente()).isFalse();
    }

    @Test
    void cadaTransicaoGravaOStatusFinalEOInstanteDaResposta() {
        Reagendamento aceita = pendente(false);
        aceita.aceitar(AGORA);
        Reagendamento recusada = pendente(false);
        recusada.recusar(AGORA);
        Reagendamento cancelada = pendente(false);
        cancelada.cancelarPeloProponente(AGORA);
        Reagendamento expirada = pendente(false);
        expirada.expirar(AGORA);
        Reagendamento semEfeito = pendente(false);
        semEfeito.encerrarSemEfeito(AGORA);

        assertThat(aceita.getStatus()).isEqualTo(StatusReagendamento.ACEITO);
        assertThat(recusada.getStatus()).isEqualTo(StatusReagendamento.RECUSADO);
        assertThat(cancelada.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
        assertThat(expirada.getStatus()).isEqualTo(StatusReagendamento.EXPIRADO);
        assertThat(semEfeito.getStatus()).isEqualTo(StatusReagendamento.SEM_EFEITO);
        assertThat(aceita.getRespondidoEm()).isEqualTo(AGORA);
        assertThat(semEfeito.getRespondidoEm()).isEqualTo(AGORA);
    }

    @Test
    void propostaJaEncerradaNaoMudaDeNovo() {
        Reagendamento recusada = pendente(false);
        recusada.recusar(AGORA);

        assertThat(recusada.estaPendente()).isFalse();
        assertThatThrownBy(() -> recusada.aceitar(AGORA.plusSeconds(1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> recusada.expirar(AGORA.plusSeconds(1))).isInstanceOf(IllegalStateException.class);
        assertThat(recusada.getStatus()).isEqualTo(StatusReagendamento.RECUSADO);
        assertThat(recusada.getRespondidoEm()).isEqualTo(AGORA);
    }

    @Test
    void estaVencidaAPartirDoInstanteDeExpiracaoInclusive() {
        Reagendamento proposta = pendente(false);

        assertThat(proposta.estaVencida(proposta.getExpiraEm().minusMillis(1))).isFalse();
        assertThat(proposta.estaVencida(proposta.getExpiraEm())).isTrue();
        assertThat(proposta.estaVencida(proposta.getExpiraEm().plusMillis(1))).isTrue();
    }

    @Test
    void foiPropostaSoParaOProponente() {
        Reagendamento proposta = pendente(false);

        assertThat(proposta.foiProposta(PROPONENTE)).isTrue();
        assertThat(proposta.foiProposta(UUID.randomUUID())).isFalse();
        assertThat(proposta.foiProposta(null)).isFalse();
    }
}
