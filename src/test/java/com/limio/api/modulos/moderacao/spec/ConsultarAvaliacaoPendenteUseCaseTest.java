package com.limio.api.modulos.moderacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.ConsultaServico;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.comum.portas.ResumoServico;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.moderacao.AvaliacaoPendente;
import com.limio.api.modulos.moderacao.OutraParte;
import com.limio.api.modulos.moderacao.actions.usecase.ConsultarAvaliacaoPendenteUseCase;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoService;
import com.limio.api.modulos.moderacao.excecao.ServicoNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class ConsultarAvaliacaoPendenteUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-03T15:00:00Z");
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();

    @Mock
    private ConsultaServico consultaServico;

    @Mock
    private AvaliacaoService avaliacaoService;

    @Mock
    private ConsultaPerfilEmpregador consultaPerfilEmpregador;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ConsultaFotoPerfil consultaFotoPerfil;

    private ConsultarAvaliacaoPendenteUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsultarAvaliacaoPendenteUseCase(consultaServico, avaliacaoService, consultaPerfilEmpregador,
                consultaDadosUsuario, consultaFotoPerfil, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void servico(UUID prestadorId, Instant concluidoEm) {
        when(consultaServico.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(
                new ResumoServico(ANUNCIO_ID, "Instalar 3 tomadas", CONTRATANTE_ID, prestadorId, concluidoEm)));
    }

    private void prestadorCadastrado() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(
                new DadosUsuario("Carlos Souza", "carlos@example.com", "11999990000", AGORA, AGORA)));
        when(consultaFotoPerfil.buscarFoto(PRESTADOR_ID)).thenReturn(Optional.empty());
    }

    @Test
    void empregadorDentroDaJanelaPodeAvaliarOPrestador() {
        servico(PRESTADOR_ID, AGORA.minus(Duration.ofDays(1)));
        prestadorCadastrado();
        when(avaliacaoService.jaAvaliou(ANUNCIO_ID, CONTRATANTE_ID)).thenReturn(false);

        AvaliacaoPendente pendente = useCase.executar(ANUNCIO_ID, CONTRATANTE_ID);

        assertThat(pendente.outraParte()).isEqualTo(new OutraParte("Carlos Souza", null, null, PapelUsuario.PRESTADOR));
        assertThat(pendente.avaliarAte()).isEqualTo(AGORA.plus(Duration.ofDays(6)));
        assertThat(pendente.podeAvaliar()).isTrue();
    }

    @Test
    void prestadorVeOEmpregadorPeloNomeDeExibicao() {
        servico(PRESTADOR_ID, AGORA.minus(Duration.ofDays(1)));
        when(consultaPerfilEmpregador.buscarResumoSemCriar(CONTRATANTE_ID)).thenReturn(Optional.of(
                new ResumoPerfilEmpregador("Maria", null, null, new BigDecimal("4.0"), 1)));
        when(avaliacaoService.jaAvaliou(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(true);

        AvaliacaoPendente pendente = useCase.executar(ANUNCIO_ID, PRESTADOR_ID);

        assertThat(pendente.outraParte()).isEqualTo(new OutraParte("Maria", null, null, PapelUsuario.EMPREGADOR));
        assertThat(pendente.podeAvaliar()).isFalse();
    }

    @Test
    void outraParteComContaEncerradaApareceComoUsuarioExcluido() {
        servico(PRESTADOR_ID, AGORA.minus(Duration.ofDays(1)));
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.empty());
        when(avaliacaoService.jaAvaliou(ANUNCIO_ID, CONTRATANTE_ID)).thenReturn(false);

        AvaliacaoPendente pendente = useCase.executar(ANUNCIO_ID, CONTRATANTE_ID);

        assertThat(pendente.outraParte()).isEqualTo(OutraParte.contaEncerrada(PapelUsuario.PRESTADOR));
    }

    @Test
    void foraDaJanelaNaoPodeMaisAvaliar() {
        servico(PRESTADOR_ID, AGORA.minus(Duration.ofDays(7)));
        prestadorCadastrado();

        AvaliacaoPendente pendente = useCase.executar(ANUNCIO_ID, CONTRATANTE_ID);

        assertThat(pendente.avaliarAte()).isEqualTo(AGORA);
        assertThat(pendente.podeAvaliar()).isFalse();
    }

    @Test
    void servicoNaoConcluidoNaoOfereceAvaliacao() {
        servico(PRESTADOR_ID, null);
        prestadorCadastrado();

        AvaliacaoPendente pendente = useCase.executar(ANUNCIO_ID, CONTRATANTE_ID);

        assertThat(pendente.outraParte().nome()).isEqualTo("Carlos Souza");
        assertThat(pendente.avaliarAte()).isNull();
        assertThat(pendente.podeAvaliar()).isFalse();
        verifyNoInteractions(avaliacaoService);
    }

    @Test
    void anuncioSemPrestadorEscolhidoRespondeSemOutraParte() {
        servico(null, null);

        AvaliacaoPendente pendente = useCase.executar(ANUNCIO_ID, CONTRATANTE_ID);

        assertThat(pendente.outraParte()).isNull();
        assertThat(pendente.avaliarAte()).isNull();
        assertThat(pendente.podeAvaliar()).isFalse();
        verifyNoInteractions(consultaDadosUsuario, consultaFotoPerfil, consultaPerfilEmpregador, avaliacaoService);
    }

    @Test
    void quemNaoEParteNaoEnxergaOServico() {
        servico(PRESTADOR_ID, AGORA.minus(Duration.ofDays(1)));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, UUID.randomUUID()))
                .isInstanceOf(ServicoNaoEncontradoException.class);
    }
}
