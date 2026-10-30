package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.portas.EscolhaDoPrestador;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.service.ConsultaEscolhasDoPrestadorService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

@ExtendWith(MockitoExtension.class)
class ConsultaEscolhasDoPrestadorServiceTest {

    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final Instant INICIO = Instant.parse("2026-10-01T03:00:00Z");
    private static final Instant FIM = Instant.parse("2026-11-01T03:00:00Z");

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private AnuncioService anuncioService;

    private ConsultaEscolhasDoPrestadorService service;

    @BeforeEach
    void montar() {
        service = new ConsultaEscolhasDoPrestadorService(aplicacaoService, anuncioService);
    }

    private static Anuncio anuncio(String titulo, Instant dataHora) {
        Anuncio anuncio = Anuncio.builder().titulo(titulo).dataHoraPretendida(dataHora).build();
        ReflectionTestUtils.setField(anuncio, "id", UUID.randomUUID());
        return anuncio;
    }

    private static Aplicacao escolhida(UUID anuncioId, StatusAplicacao status, Instant escolhidaEm) {
        return Aplicacao.builder().anuncioId(anuncioId).prestadorId(PRESTADOR_ID).status(status)
                .escolhidaEm(escolhidaEm).build();
    }

    @Test
    void deveJuntarCadaEscolhaAoSeuAnuncioNaOrdemDasEscolhas() {
        Anuncio faxina = anuncio("Faxina", Instant.parse("2026-10-30T13:00:00Z"));
        Anuncio pintura = anuncio("Pintura", Instant.parse("2026-10-25T13:00:00Z"));
        Instant recente = Instant.parse("2026-10-20T12:00:00Z");
        Instant antiga = Instant.parse("2026-10-05T12:00:00Z");
        when(aplicacaoService.buscarEscolhidasDoPrestadorEntre(PRESTADOR_ID, INICIO, FIM)).thenReturn(List.of(
                escolhida(faxina.getId(), StatusAplicacao.ESCOLHIDA, recente),
                escolhida(pintura.getId(), StatusAplicacao.CONCLUIDA, antiga)));
        when(anuncioService.buscarPorIds(List.of(faxina.getId(), pintura.getId()))).thenReturn(List.of(pintura, faxina));

        assertThat(service.listarEscolhidasEntre(PRESTADOR_ID, INICIO, FIM)).containsExactly(
                new EscolhaDoPrestador(faxina.getId(), "Faxina", faxina.getDataHoraPretendida(), recente, "ESCOLHIDA"),
                new EscolhaDoPrestador(pintura.getId(), "Pintura", pintura.getDataHoraPretendida(), antiga,
                        "CONCLUIDA"));
    }

    @Test
    void escolhaDeAnuncioQueSumiuFicaDeFora() {
        UUID sumido = UUID.randomUUID();
        when(aplicacaoService.buscarEscolhidasDoPrestadorEntre(PRESTADOR_ID, INICIO, FIM)).thenReturn(List.of(
                escolhida(sumido, StatusAplicacao.ESCOLHIDA, INICIO)));
        when(anuncioService.buscarPorIds(List.of(sumido))).thenReturn(List.of());

        assertThat(service.listarEscolhidasEntre(PRESTADOR_ID, INICIO, FIM)).isEmpty();
    }
}
