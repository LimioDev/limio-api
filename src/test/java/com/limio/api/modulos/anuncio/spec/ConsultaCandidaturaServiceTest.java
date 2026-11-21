package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ResumoCandidatura;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.service.ConsultaCandidaturaService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

@ExtendWith(MockitoExtension.class)
class ConsultaCandidaturaServiceTest {

    private static final UUID EMPREGADOR_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID CATEGORIA_ID = UUID.randomUUID();

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private ConsultaCategoria consultaCategoria;

    private ConsultaCandidaturaService service;

    @BeforeEach
    void montarService() {
        service = new ConsultaCandidaturaService(aplicacaoService, anuncioService, consultaCategoria);
    }

    private static Anuncio anuncio(String titulo) {
        Anuncio anuncio = Anuncio.builder().contratanteId(EMPREGADOR_ID).titulo(titulo).categoriaId(CATEGORIA_ID).build();
        ReflectionTestUtils.setField(anuncio, "id", UUID.randomUUID());
        return anuncio;
    }

    private static Aplicacao aplicacao(Anuncio anuncio, StatusAplicacao status) {
        Aplicacao aplicacao = Aplicacao.builder().anuncioId(anuncio.getId()).prestadorId(PRESTADOR_ID).status(status).build();
        ReflectionTestUtils.setField(aplicacao, "id", UUID.randomUUID());
        return aplicacao;
    }

    @Test
    void dadoVariasCandidaturas_quandoBuscaEmLote_entaoUmaConsultaPorTabelaComTituloStatusECategoria() {
        Anuncio tomadas = anuncio("Instalar 3 tomadas");
        Anuncio pintura = anuncio("Pintar a sala");
        Aplicacao enviada = aplicacao(tomadas, StatusAplicacao.ENVIADA);
        Aplicacao recusada = aplicacao(pintura, StatusAplicacao.RECUSADA);
        List<UUID> ids = List.of(enviada.getId(), recusada.getId());
        when(aplicacaoService.buscarPorIds(ids)).thenReturn(List.of(enviada, recusada));
        when(anuncioService.buscarPorIds(anyCollection())).thenReturn(List.of(tomadas, pintura));
        when(consultaCategoria.buscarPorIdsEmLote(anyCollection())).thenReturn(
                Map.of(CATEGORIA_ID, new ResumoCategoria(CATEGORIA_ID, "Elétrica", "Reparos", true)));

        Map<UUID, ResumoCandidatura> resumos = service.buscarResumosEmLote(ids);

        assertThat(resumos).containsOnlyKeys(enviada.getId(), recusada.getId());
        assertThat(resumos.get(enviada.getId())).isEqualTo(new ResumoCandidatura(enviada.getId(), "ENVIADA",
                tomadas.getId(), EMPREGADOR_ID, PRESTADOR_ID, "Instalar 3 tomadas", "Elétrica"));
        assertThat(resumos.get(recusada.getId()).status()).isEqualTo("RECUSADA");
        assertThat(resumos.get(recusada.getId()).anuncioTitulo()).isEqualTo("Pintar a sala");
        verify(aplicacaoService).buscarPorIds(ids);
        verify(anuncioService).buscarPorIds(anyCollection());
        verify(consultaCategoria).buscarPorIdsEmLote(anyCollection());
        verifyNoMoreInteractions(aplicacaoService, anuncioService, consultaCategoria);
    }

    @Test
    void dadaCandidaturaInexistente_quandoBuscaEmLote_entaoNaoVemNoMapa() {
        UUID inexistente = UUID.randomUUID();
        when(aplicacaoService.buscarPorIds(List.of(inexistente))).thenReturn(List.of());
        when(anuncioService.buscarPorIds(List.of())).thenReturn(List.of());
        when(consultaCategoria.buscarPorIdsEmLote(List.of())).thenReturn(Map.of());

        assertThat(service.buscarResumosEmLote(List.of(inexistente))).isEmpty();
    }

    @Test
    void dadaCandidaturaSemAnuncio_quandoBuscaEmLote_entaoFicaDeFora() {
        Anuncio semRegistro = anuncio("Sumiu");
        Aplicacao orfa = aplicacao(semRegistro, StatusAplicacao.ENVIADA);
        when(aplicacaoService.buscarPorIds(List.of(orfa.getId()))).thenReturn(List.of(orfa));
        when(anuncioService.buscarPorIds(anyCollection())).thenReturn(List.of());
        when(consultaCategoria.buscarPorIdsEmLote(anyCollection())).thenReturn(Map.of());

        assertThat(service.buscarResumosEmLote(List.of(orfa.getId()))).isEmpty();
    }

    @Test
    void dadaCategoriaAusente_quandoBuscaEmLote_entaoFalhaPorDadoCorrompido() {
        Anuncio tomadas = anuncio("Instalar 3 tomadas");
        Aplicacao enviada = aplicacao(tomadas, StatusAplicacao.ENVIADA);
        when(aplicacaoService.buscarPorIds(List.of(enviada.getId()))).thenReturn(List.of(enviada));
        when(anuncioService.buscarPorIds(anyCollection())).thenReturn(List.of(tomadas));
        when(consultaCategoria.buscarPorIdsEmLote(anyCollection())).thenReturn(Map.of());

        assertThatThrownBy(() -> service.buscarResumosEmLote(List.of(enviada.getId())))
                .isInstanceOf(IllegalStateException.class);
    }
}
