package com.limio.api.modulos.moderacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.limio.api.comum.portas.AvaliacaoRecente;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoAvaliacoes;
import com.limio.api.comum.portas.SituacaoAvaliacao;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.moderacao.actions.service.ConsultaAvaliacoesService;
import com.limio.api.modulos.moderacao.avaliacao.Avaliacao;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoRepository;
import com.limio.api.modulos.moderacao.avaliacao.ResumoAvaliacaoProjecao;

@ExtendWith(MockitoExtension.class)
class ConsultaAvaliacoesServiceTest {

    private static final UUID AVALIADO_ID = UUID.randomUUID();
    private static final UUID AVALIADOR_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-20T12:00:00Z");

    @Mock
    private AvaliacaoRepository avaliacaoRepository;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    private ConsultaAvaliacoesService service;

    @BeforeEach
    void montarService() {
        service = new ConsultaAvaliacoesService(avaliacaoRepository, consultaDadosUsuario,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static ResumoAvaliacaoProjecao projecao(UUID avaliadoId, Double media, int qtd) {
        return new ResumoAvaliacaoProjecao() {
            @Override
            public UUID getAvaliadoId() {
                return avaliadoId;
            }

            @Override
            public Double getMedia() {
                return media;
            }

            @Override
            public int getQtd() {
                return qtd;
            }
        };
    }

    @Test
    void dadoIdsVazios_quandoResumir_entaoMapaVazioSemConsultarBanco() {
        assertThat(service.resumir(List.of())).isEmpty();
    }

    @Test
    void dadoPrestadorComAvaliacoes_quandoResumir_entaoMediaComUmaCasaArredondadaComercialmente() {
        when(avaliacaoRepository.resumirPorAvaliados(List.of(AVALIADO_ID), PapelUsuario.PRESTADOR))
                .thenReturn(List.of(projecao(AVALIADO_ID, 4.45, 3)));

        Map<UUID, ResumoAvaliacoes> resumo = service.resumir(List.of(AVALIADO_ID));

        assertThat(resumo).containsEntry(AVALIADO_ID, new ResumoAvaliacoes(3, new BigDecimal("4.5")));
    }

    @Test
    void dadoAvaliadoForaDoMapa_quandoResumir_entaoNaoAparece() {
        when(avaliacaoRepository.resumirPorAvaliados(List.of(AVALIADO_ID), PapelUsuario.PRESTADOR))
                .thenReturn(List.of());

        assertThat(service.resumir(List.of(AVALIADO_ID))).isEmpty();
    }

    @Test
    void dadoAvaliadorComContaAtiva_quandoListarRecentes_entaoNomeAbreviado() {
        Avaliacao avaliacao = Avaliacao.publicar(UUID.randomUUID(), AVALIADOR_ID, AVALIADO_ID, PapelUsuario.PRESTADOR,
                5, "Ótimo serviço!", Instant.parse("2026-10-20T14:00:00Z"));
        when(avaliacaoRepository.findByAvaliadoIdAndPapelAvaliadoOrderByPublicadaEmDesc(AVALIADO_ID,
                PapelUsuario.PRESTADOR, PageRequest.of(0, 5))).thenReturn(List.of(avaliacao));
        when(consultaDadosUsuario.buscarPorIds(List.of(AVALIADOR_ID)))
                .thenReturn(Map.of(AVALIADOR_ID, new DadosUsuario("Camila Rocha", "camila@example.com", null, null)));

        List<AvaliacaoRecente> recentes = service.listarRecentes(AVALIADO_ID, PapelUsuario.PRESTADOR, 5);

        assertThat(recentes).containsExactly(
                new AvaliacaoRecente("Camila R.", 5, "Ótimo serviço!", Instant.parse("2026-10-20T14:00:00Z")));
    }

    @Test
    void dadoAvaliadorComContaEncerrada_quandoListarRecentes_entaoNomeContaEncerrada() {
        Avaliacao avaliacao = Avaliacao.publicar(UUID.randomUUID(), AVALIADOR_ID, AVALIADO_ID, PapelUsuario.EMPREGADOR,
                4, null, Instant.parse("2026-10-20T14:00:00Z"));
        when(avaliacaoRepository.findByAvaliadoIdAndPapelAvaliadoOrderByPublicadaEmDesc(AVALIADO_ID,
                PapelUsuario.EMPREGADOR, PageRequest.of(0, 5))).thenReturn(List.of(avaliacao));
        when(consultaDadosUsuario.buscarPorIds(List.of(AVALIADOR_ID))).thenReturn(Map.of());

        List<AvaliacaoRecente> recentes = service.listarRecentes(AVALIADO_ID, PapelUsuario.EMPREGADOR, 5);

        assertThat(recentes).extracting(AvaliacaoRecente::avaliadorNome).containsExactly("Conta encerrada");
    }

    // ---- situação de avaliação por serviço (ETI-71)

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID ANUNCIO_A = UUID.randomUUID();
    private static final UUID ANUNCIO_B = UUID.randomUUID();

    private void semAvaliacoes(Collection<UUID> anuncioIds) {
        when(avaliacaoRepository.findByAvaliadoIdAndAnuncioIdIn(USUARIO_ID, anuncioIds)).thenReturn(List.of());
        when(avaliacaoRepository.findAnuncioIdsAvaliadosPor(USUARIO_ID, anuncioIds)).thenReturn(List.of());
    }

    @Test
    void dadoSemServicos_quandoSituacaoPorServico_entaoMapaVazioSemConsultarBanco() {
        assertThat(service.situacaoPorServico(USUARIO_ID, Map.of())).isEmpty();

        verifyNoInteractions(avaliacaoRepository);
    }

    @Test
    void dadoNotaRecebida_quandoSituacaoPorServico_entaoDevolveANotaDaOutraParte() {
        Avaliacao recebida = Avaliacao.publicar(ANUNCIO_A, AVALIADOR_ID, USUARIO_ID, PapelUsuario.PRESTADOR, 4, null,
                AGORA.minusSeconds(60));
        when(avaliacaoRepository.findByAvaliadoIdAndAnuncioIdIn(USUARIO_ID, Set.of(ANUNCIO_A)))
                .thenReturn(List.of(recebida));
        when(avaliacaoRepository.findAnuncioIdsAvaliadosPor(USUARIO_ID, Set.of(ANUNCIO_A))).thenReturn(List.of());

        Map<UUID, SituacaoAvaliacao> situacao = service.situacaoPorServico(USUARIO_ID,
                Map.of(ANUNCIO_A, AGORA.minus(Duration.ofDays(1))));

        assertThat(situacao.get(ANUNCIO_A).notaRecebida()).isEqualTo(4);
    }

    @Test
    void dadoOutraParteQueNaoAvaliou_quandoSituacaoPorServico_entaoNotaNula() {
        semAvaliacoes(Set.of(ANUNCIO_A));

        Map<UUID, SituacaoAvaliacao> situacao = service.situacaoPorServico(USUARIO_ID,
                Map.of(ANUNCIO_A, AGORA.minus(Duration.ofDays(1))));

        assertThat(situacao.get(ANUNCIO_A).notaRecebida()).isNull();
    }

    @Test
    void dadoJanelaAbertaESemAvaliacaoMinha_quandoSituacaoPorServico_entaoPodeAvaliar() {
        semAvaliacoes(Set.of(ANUNCIO_A));

        Map<UUID, SituacaoAvaliacao> situacao = service.situacaoPorServico(USUARIO_ID,
                Map.of(ANUNCIO_A, AGORA.minus(Duration.ofDays(6))));

        assertThat(situacao.get(ANUNCIO_A).podeAvaliar()).isTrue();
    }

    @Test
    void dadoExatamenteSeteDiasDepoisDaConclusao_quandoSituacaoPorServico_entaoJanelaFechada() {
        semAvaliacoes(Set.of(ANUNCIO_A));

        Map<UUID, SituacaoAvaliacao> situacao = service.situacaoPorServico(USUARIO_ID,
                Map.of(ANUNCIO_A, AGORA.minus(Duration.ofDays(7))));

        assertThat(situacao.get(ANUNCIO_A).podeAvaliar()).isFalse();
    }

    @Test
    void dadoUmSegundoAntesDeFecharAJanela_quandoSituacaoPorServico_entaoAindaPodeAvaliar() {
        semAvaliacoes(Set.of(ANUNCIO_A));

        Map<UUID, SituacaoAvaliacao> situacao = service.situacaoPorServico(USUARIO_ID,
                Map.of(ANUNCIO_A, AGORA.minus(Duration.ofDays(7)).plusSeconds(1)));

        assertThat(situacao.get(ANUNCIO_A).podeAvaliar()).isTrue();
    }

    @Test
    void dadoQueEuJaAvaliei_quandoSituacaoPorServico_entaoNaoPodeAvaliarDeNovo() {
        when(avaliacaoRepository.findByAvaliadoIdAndAnuncioIdIn(USUARIO_ID, Set.of(ANUNCIO_A))).thenReturn(List.of());
        when(avaliacaoRepository.findAnuncioIdsAvaliadosPor(USUARIO_ID, Set.of(ANUNCIO_A)))
                .thenReturn(List.of(ANUNCIO_A));

        Map<UUID, SituacaoAvaliacao> situacao = service.situacaoPorServico(USUARIO_ID,
                Map.of(ANUNCIO_A, AGORA.minus(Duration.ofDays(1))));

        assertThat(situacao.get(ANUNCIO_A).podeAvaliar()).isFalse();
    }

    @Test
    void dadoServicoSemDataDeConclusao_quandoSituacaoPorServico_entaoNaoPodeAvaliar() {
        Map<UUID, Instant> semData = new HashMap<>();
        semData.put(ANUNCIO_A, null);
        semAvaliacoes(Set.of(ANUNCIO_A));

        assertThat(service.situacaoPorServico(USUARIO_ID, semData).get(ANUNCIO_A))
                .isEqualTo(new SituacaoAvaliacao(null, false));
    }

    @Test
    void dadoVariosServicos_quandoSituacaoPorServico_entaoCadaUmTemASuaSituacaoEConsultaEmLote() {
        Avaliacao recebidaDeB = Avaliacao.publicar(ANUNCIO_B, AVALIADOR_ID, USUARIO_ID, PapelUsuario.EMPREGADOR, 5,
                "Ótimo", AGORA.minusSeconds(60));
        Set<UUID> ids = Set.of(ANUNCIO_A, ANUNCIO_B);
        when(avaliacaoRepository.findByAvaliadoIdAndAnuncioIdIn(USUARIO_ID, ids)).thenReturn(List.of(recebidaDeB));
        when(avaliacaoRepository.findAnuncioIdsAvaliadosPor(USUARIO_ID, ids)).thenReturn(List.of(ANUNCIO_B));

        Map<UUID, SituacaoAvaliacao> situacao = service.situacaoPorServico(USUARIO_ID,
                Map.of(ANUNCIO_A, AGORA.minus(Duration.ofDays(2)), ANUNCIO_B, AGORA.minus(Duration.ofDays(2))));

        assertThat(situacao).containsEntry(ANUNCIO_A, new SituacaoAvaliacao(null, true))
                .containsEntry(ANUNCIO_B, new SituacaoAvaliacao(5, false));
        verify(avaliacaoRepository, times(1)).findByAvaliadoIdAndAnuncioIdIn(USUARIO_ID, ids);
        verify(avaliacaoRepository, times(1)).findAnuncioIdsAvaliadosPor(USUARIO_ID, ids);
    }

    @Test
    void dadoSemAvaliacoes_quandoListarRecentes_entaoListaVazia() {
        when(avaliacaoRepository.findByAvaliadoIdAndPapelAvaliadoOrderByPublicadaEmDesc(AVALIADO_ID,
                PapelUsuario.PRESTADOR, PageRequest.of(0, 5))).thenReturn(List.of());

        assertThat(service.listarRecentes(AVALIADO_ID, PapelUsuario.PRESTADOR, 5)).isEmpty();
    }
}
