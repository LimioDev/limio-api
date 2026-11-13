package com.limio.api.modulos.moderacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.moderacao.actions.service.ConsultaAvaliacoesService;
import com.limio.api.modulos.moderacao.avaliacao.Avaliacao;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoRepository;
import com.limio.api.modulos.moderacao.avaliacao.ResumoAvaliacaoProjecao;

@ExtendWith(MockitoExtension.class)
class ConsultaAvaliacoesServiceTest {

    private static final UUID AVALIADO_ID = UUID.randomUUID();
    private static final UUID AVALIADOR_ID = UUID.randomUUID();

    @Mock
    private AvaliacaoRepository avaliacaoRepository;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    private ConsultaAvaliacoesService service;

    @BeforeEach
    void montarService() {
        service = new ConsultaAvaliacoesService(avaliacaoRepository, consultaDadosUsuario);
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
        when(consultaDadosUsuario.buscarPorId(AVALIADOR_ID))
                .thenReturn(Optional.of(new DadosUsuario("Camila Rocha", "camila@example.com", null, null)));

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
        when(consultaDadosUsuario.buscarPorId(AVALIADOR_ID)).thenReturn(Optional.empty());

        List<AvaliacaoRecente> recentes = service.listarRecentes(AVALIADO_ID, PapelUsuario.EMPREGADOR, 5);

        assertThat(recentes).extracting(AvaliacaoRecente::avaliadorNome).containsExactly("Conta encerrada");
    }

    @Test
    void dadoSemAvaliacoes_quandoListarRecentes_entaoListaVazia() {
        when(avaliacaoRepository.findByAvaliadoIdAndPapelAvaliadoOrderByPublicadaEmDesc(AVALIADO_ID,
                PapelUsuario.PRESTADOR, PageRequest.of(0, 5))).thenReturn(List.of());

        assertThat(service.listarRecentes(AVALIADO_ID, PapelUsuario.PRESTADOR, 5)).isEmpty();
    }
}
