package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.portas.ConsultaAvaliacoes;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.SituacaoAvaliacao;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.comum.texto.NomeHelper;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.CriterioDoHistorico;
import com.limio.api.modulos.anuncio.ItemHistorico;
import com.limio.api.modulos.anuncio.actions.usecase.ListarHistoricoUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.CausaEncerramento;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.enums.StatusHistorico;

@ExtendWith(MockitoExtension.class)
class ListarHistoricoUseCaseTest {

    private static final UUID EMPREGADOR_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final Instant VALIDADO_EM = Instant.parse("2026-10-12T17:40:00Z");

    @Mock
    private AnuncioService anuncioService;
    @Mock
    private AplicacaoService aplicacaoService;
    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;
    @Mock
    private ConsultaAvaliacoes consultaAvaliacoes;

    private ListarHistoricoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ListarHistoricoUseCase(anuncioService, aplicacaoService, consultaDadosUsuario,
                consultaAvaliacoes);
    }

    private Anuncio anuncio(StatusAnuncio status, CausaEncerramento causa) {
        Anuncio anuncio = Anuncio.builder().contratanteId(EMPREGADOR_ID).titulo("Faxina").descricao("Faxina")
                .dataHoraPretendida(Instant.now()).status(status).build();
        ReflectionTestUtils.setField(anuncio, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(anuncio, "causaEncerramento", causa);
        if (status == StatusAnuncio.CONCLUIDO) {
            ReflectionTestUtils.setField(anuncio, "validadoEm", VALIDADO_EM);
        } else {
            ReflectionTestUtils.setField(anuncio, "encerradoEm", VALIDADO_EM);
        }
        return anuncio;
    }

    private Aplicacao aplicacao(UUID anuncioId, UUID prestadorId, Instant escolhidaEm) {
        Aplicacao aplicacao = Aplicacao.builder().anuncioId(anuncioId).prestadorId(prestadorId)
                .status(StatusAplicacao.ENVIADA).dataAplicacao(Instant.now()).build();
        ReflectionTestUtils.setField(aplicacao, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(aplicacao, "escolhidaEm", escolhidaEm);
        return aplicacao;
    }

    private static DadosUsuario dados(String nome) {
        return new DadosUsuario(nome, "x@x.com", "+5511999998888", Instant.now());
    }

    private static Page<Anuncio> pagina(Anuncio... anuncios) {
        return new PageImpl<>(List.of(anuncios), PageRequest.of(0, 20), anuncios.length);
    }

    private Page<ItemHistorico> comoEmpregador(StatusHistorico status) {
        return useCase.executar(EMPREGADOR_ID, PapelUsuario.EMPREGADOR, status, null, null, 0, 20);
    }

    private Page<ItemHistorico> comoPrestador(StatusHistorico status) {
        return useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, status, null, null, 0, 20);
    }

    @Test
    void dadoEmpregador_quandoListar_entaoConsultaSoAsDeleEPassaOCriterio() {
        when(anuncioService.buscarHistoricoDoContratante(eq(EMPREGADOR_ID), any(), anyInt(), anyInt()))
                .thenReturn(pagina());

        useCase.executar(EMPREGADOR_ID, PapelUsuario.EMPREGADOR, StatusHistorico.CANCELADO,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 2, 10);

        ArgumentCaptor<CriterioDoHistorico> criterio = ArgumentCaptor.forClass(CriterioDoHistorico.class);
        verify(anuncioService).buscarHistoricoDoContratante(eq(EMPREGADOR_ID), criterio.capture(), eq(2), eq(10));
        assertThat(criterio.getValue().status()).isEqualTo(StatusHistorico.CANCELADO);
        assertThat(criterio.getValue().de()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
        assertThat(criterio.getValue().ate()).isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
        verifyNoInteractions(consultaAvaliacoes);
    }

    @Test
    void dadoPrestador_quandoListar_entaoUsaAConsultaDoPrestador() {
        when(anuncioService.buscarHistoricoDoPrestador(eq(PRESTADOR_ID), any(), anyInt(), anyInt()))
                .thenReturn(pagina());

        Page<ItemHistorico> resultado = comoPrestador(null);

        assertThat(resultado.getContent()).isEmpty();
        verifyNoInteractions(consultaAvaliacoes);
    }

    @Test
    void dadoPeriodoInvertido_quandoListar_entaoLancaIllegalArgument() {
        assertThatThrownBy(() -> useCase.executar(EMPREGADOR_ID, PapelUsuario.EMPREGADOR, null,
                LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1), 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(anuncioService);
    }

    @Test
    void dadoConcluidoComPrestadorEscolhido_quandoEmpregadorListar_entaoNomeCompletoEAvaliacao() {
        Anuncio concluido = anuncio(StatusAnuncio.CONCLUIDO, null);
        when(anuncioService.buscarHistoricoDoContratante(eq(EMPREGADOR_ID), any(), anyInt(), anyInt()))
                .thenReturn(pagina(concluido));
        when(aplicacaoService.buscarEscolhidasDosAnuncios(any()))
                .thenReturn(List.of(aplicacao(concluido.getId(), PRESTADOR_ID, VALIDADO_EM)));
        when(consultaDadosUsuario.buscarPorIds(any())).thenReturn(Map.of(PRESTADOR_ID, dados("Maria Souza Lima")));
        when(consultaAvaliacoes.situacaoPorServico(eq(EMPREGADOR_ID), anyMap()))
                .thenReturn(Map.of(concluido.getId(), new SituacaoAvaliacao(4, true)));

        ItemHistorico item = comoEmpregador(null).getContent().get(0);

        assertThat(item.status()).isEqualTo(StatusHistorico.CONCLUIDO);
        assertThat(item.outraParte()).isEqualTo("Maria Souza Lima");
        assertThat(item.avaliacao()).isEqualTo(new SituacaoAvaliacao(4, true));
        ArgumentCaptor<Map<UUID, Instant>> enviado = ArgumentCaptor.forClass(Map.class);
        verify(consultaAvaliacoes).situacaoPorServico(eq(EMPREGADOR_ID), enviado.capture());
        assertThat(enviado.getValue()).containsExactly(Map.entry(concluido.getId(), VALIDADO_EM));
    }

    @Test
    void dadoExpiradoSemEscolhido_quandoEmpregadorListar_entaoSemOutraParteESemAvaliacao() {
        Anuncio expirado = anuncio(StatusAnuncio.ENCERRADO, CausaEncerramento.EXPIRACAO);
        when(anuncioService.buscarHistoricoDoContratante(eq(EMPREGADOR_ID), any(), anyInt(), anyInt()))
                .thenReturn(pagina(expirado));
        when(aplicacaoService.buscarEscolhidasDosAnuncios(any())).thenReturn(List.of());

        ItemHistorico item = comoEmpregador(null).getContent().get(0);

        assertThat(item.status()).isEqualTo(StatusHistorico.EXPIRADO);
        assertThat(item.outraParte()).isNull();
        assertThat(item.avaliacao()).isNull();
        verifyNoInteractions(consultaAvaliacoes);
    }

    @Test
    void dadoCanceladoComEscolhidoQueSaiu_quandoEmpregadorListar_entaoContaEncerrada() {
        Anuncio cancelado = anuncio(StatusAnuncio.ENCERRADO, CausaEncerramento.CANCELAMENTO);
        when(anuncioService.buscarHistoricoDoContratante(eq(EMPREGADOR_ID), any(), anyInt(), anyInt()))
                .thenReturn(pagina(cancelado));
        when(aplicacaoService.buscarEscolhidasDosAnuncios(any()))
                .thenReturn(List.of(aplicacao(cancelado.getId(), PRESTADOR_ID, VALIDADO_EM)));
        when(consultaDadosUsuario.buscarPorIds(any())).thenReturn(Map.of());

        ItemHistorico item = comoEmpregador(null).getContent().get(0);

        assertThat(item.outraParte()).isEqualTo(NomeHelper.CONTA_ENCERRADA);
        assertThat(item.avaliacao()).isNull();
    }

    @Test
    void dadoDoisEscolhidosNoMesmoAnuncio_quandoEmpregadorListar_entaoVaiAMaisRecente() {
        Anuncio cancelado = anuncio(StatusAnuncio.ENCERRADO, CausaEncerramento.CANCELAMENTO);
        UUID primeiro = UUID.randomUUID();
        when(anuncioService.buscarHistoricoDoContratante(eq(EMPREGADOR_ID), any(), anyInt(), anyInt()))
                .thenReturn(pagina(cancelado));
        when(aplicacaoService.buscarEscolhidasDosAnuncios(any())).thenReturn(List.of(
                aplicacao(cancelado.getId(), PRESTADOR_ID, VALIDADO_EM),
                aplicacao(cancelado.getId(), primeiro, VALIDADO_EM.minusSeconds(3600))));
        when(consultaDadosUsuario.buscarPorIds(any())).thenReturn(Map.of(PRESTADOR_ID, dados("Joao Pereira"),
                primeiro, dados("Antigo Escolhido")));

        assertThat(comoEmpregador(null).getContent().get(0).outraParte()).isEqualTo("Joao Pereira");
    }

    @Test
    void dadoPrestadorEscolhidoEmConcluido_quandoListar_entaoNomeCompletoDoEmpregadorEAvaliacao() {
        Anuncio concluido = anuncio(StatusAnuncio.CONCLUIDO, null);
        when(anuncioService.buscarHistoricoDoPrestador(eq(PRESTADOR_ID), any(), anyInt(), anyInt()))
                .thenReturn(pagina(concluido));
        when(aplicacaoService.buscarDoPrestadorNosAnuncios(eq(PRESTADOR_ID), any())).thenReturn(List.of(
                aplicacao(concluido.getId(), PRESTADOR_ID, null),
                aplicacao(concluido.getId(), PRESTADOR_ID, VALIDADO_EM)));
        when(consultaDadosUsuario.buscarPorIds(any())).thenReturn(Map.of(EMPREGADOR_ID, dados("Ana Paula Ramos")));
        when(consultaAvaliacoes.situacaoPorServico(eq(PRESTADOR_ID), anyMap()))
                .thenReturn(Map.of(concluido.getId(), new SituacaoAvaliacao(null, true)));

        ItemHistorico item = comoPrestador(null).getContent().get(0);

        assertThat(item.outraParte()).isEqualTo("Ana Paula Ramos");
        assertThat(item.avaliacao()).isEqualTo(new SituacaoAvaliacao(null, true));
    }

    @Test
    void dadoPrestadorNaoEscolhidoEmConcluido_quandoListar_entaoNomeAbreviadoESemAvaliacao() {
        Anuncio concluido = anuncio(StatusAnuncio.CONCLUIDO, null);
        when(anuncioService.buscarHistoricoDoPrestador(eq(PRESTADOR_ID), any(), anyInt(), anyInt()))
                .thenReturn(pagina(concluido));
        when(aplicacaoService.buscarDoPrestadorNosAnuncios(eq(PRESTADOR_ID), any()))
                .thenReturn(List.of(aplicacao(concluido.getId(), PRESTADOR_ID, null)));
        when(consultaDadosUsuario.buscarPorIds(any())).thenReturn(Map.of(EMPREGADOR_ID, dados("Ana Paula Ramos")));

        ItemHistorico item = comoPrestador(null).getContent().get(0);

        assertThat(item.outraParte()).isEqualTo("Ana R.");
        assertThat(item.avaliacao()).isNull();
        verifyNoInteractions(consultaAvaliacoes);
    }

    @Test
    void dadoEmpregadorComContaEncerrada_quandoPrestadorListar_entaoContaEncerrada() {
        Anuncio cancelado = anuncio(StatusAnuncio.ENCERRADO, CausaEncerramento.CANCELAMENTO);
        when(anuncioService.buscarHistoricoDoPrestador(eq(PRESTADOR_ID), any(), anyInt(), anyInt()))
                .thenReturn(pagina(cancelado));
        when(aplicacaoService.buscarDoPrestadorNosAnuncios(eq(PRESTADOR_ID), any()))
                .thenReturn(List.of(aplicacao(cancelado.getId(), PRESTADOR_ID, VALIDADO_EM)));
        when(consultaDadosUsuario.buscarPorIds(any())).thenReturn(Map.of());

        assertThat(comoPrestador(null).getContent().get(0).outraParte()).isEqualTo(NomeHelper.CONTA_ENCERRADA);
    }

    @Test
    void dadoEncerradoSemCausa_quandoListar_entaoPulaOItemEMantemOTotal() {
        Anuncio semCausa = anuncio(StatusAnuncio.ENCERRADO, null);
        when(anuncioService.buscarHistoricoDoContratante(eq(EMPREGADOR_ID), any(), anyInt(), anyInt()))
                .thenReturn(pagina(semCausa));
        when(aplicacaoService.buscarEscolhidasDosAnuncios(any())).thenReturn(List.of());

        Page<ItemHistorico> resultado = comoEmpregador(null);

        assertThat(resultado.getContent()).isEmpty();
        assertThat(resultado.getTotalElements()).isEqualTo(1);
    }
}
