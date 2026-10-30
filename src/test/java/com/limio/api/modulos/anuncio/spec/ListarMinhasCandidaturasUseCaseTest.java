package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.MinhaCandidatura;
import com.limio.api.modulos.anuncio.actions.usecase.ListarMinhasCandidaturasUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.AbaCandidatura;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

@ExtendWith(MockitoExtension.class)
class ListarMinhasCandidaturasUseCaseTest {

    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID EMPREGADOR_ID = UUID.randomUUID();

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    private ListarMinhasCandidaturasUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ListarMinhasCandidaturasUseCase(aplicacaoService, anuncioService, consultaDadosUsuario);
    }

    private Anuncio anuncio(UUID contratanteId) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(contratanteId)
                .titulo("Faxina")
                .descricao("Faxina")
                .dataHoraPretendida(Instant.now())
                .status(StatusAnuncio.PUBLICADO)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", UUID.randomUUID());
        return anuncio;
    }

    private Aplicacao aplicacao(UUID anuncioId) {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(PRESTADOR_ID)
                .status(StatusAplicacao.ENVIADA)
                .dataAplicacao(Instant.now())
                .build();
        ReflectionTestUtils.setField(aplicacao, "id", UUID.randomUUID());
        return aplicacao;
    }

    private void pagina(int numero, long total, Aplicacao... aplicacoes) {
        Page<Aplicacao> page = new PageImpl<>(List.of(aplicacoes), PageRequest.of(numero, 20), total);
        when(aplicacaoService.buscarDoPrestadorPorAba(eq(PRESTADOR_ID), eq(AbaCandidatura.PENDENTES), eq(numero),
                anyInt())).thenReturn(page);
    }

    private static DadosUsuario dados(String nome) {
        return new DadosUsuario(nome, "x@example.com", "+5511999998888", Instant.now(), Instant.now());
    }

    @Test
    void dadoCandidaturas_quandoLista_entaoJuntaAnuncioENomeEIniciaisDoEmpregador() {
        Anuncio anuncio = anuncio(EMPREGADOR_ID);
        Aplicacao aplicacao = aplicacao(anuncio.getId());
        pagina(0, 1, aplicacao);
        when(anuncioService.buscarPorIds(List.of(anuncio.getId()))).thenReturn(List.of(anuncio));
        when(consultaDadosUsuario.buscarPorId(EMPREGADOR_ID)).thenReturn(Optional.of(dados("Maria da Silva")));

        Page<MinhaCandidatura> resultado = useCase.executar(PRESTADOR_ID, AbaCandidatura.PENDENTES, 0);

        assertThat(resultado.getContent()).singleElement().satisfies(item -> {
            assertThat(item.aplicacao()).isSameAs(aplicacao);
            assertThat(item.anuncio()).isSameAs(anuncio);
            assertThat(item.nomeEmpregador()).isEqualTo("Maria S.");
            assertThat(item.iniciaisEmpregador()).isEqualTo("MS");
        });
        assertThat(resultado.getTotalElements()).isEqualTo(1);
    }

    @Test
    void dadoVariosAnunciosDoMesmoEmpregador_quandoLista_entaoBuscaONomeUmaVezSo() {
        Anuncio primeiro = anuncio(EMPREGADOR_ID);
        Anuncio segundo = anuncio(EMPREGADOR_ID);
        pagina(0, 2, aplicacao(primeiro.getId()), aplicacao(segundo.getId()));
        when(anuncioService.buscarPorIds(anyList())).thenReturn(List.of(primeiro, segundo));
        when(consultaDadosUsuario.buscarPorId(EMPREGADOR_ID)).thenReturn(Optional.of(dados("Maria da Silva")));

        assertThat(useCase.executar(PRESTADOR_ID, AbaCandidatura.PENDENTES, 0).getContent()).hasSize(2);
        verify(consultaDadosUsuario, times(1)).buscarPorId(EMPREGADOR_ID);
    }

    @Test
    void dadoContaDoEmpregadorEncerrada_quandoLista_entaoItemSemNome() {
        Anuncio anuncio = anuncio(EMPREGADOR_ID);
        pagina(0, 1, aplicacao(anuncio.getId()));
        when(anuncioService.buscarPorIds(anyList())).thenReturn(List.of(anuncio));
        when(consultaDadosUsuario.buscarPorId(EMPREGADOR_ID)).thenReturn(Optional.empty());

        assertThat(useCase.executar(PRESTADOR_ID, AbaCandidatura.PENDENTES, 0).getContent())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.nomeEmpregador()).isNull();
                    assertThat(item.iniciaisEmpregador()).isNull();
                });
    }

    @Test
    void dadoAnuncioInexistente_quandoLista_entaoPulaOItemEMantemOsOutros() {
        Anuncio existente = anuncio(EMPREGADOR_ID);
        pagina(0, 2, aplicacao(UUID.randomUUID()), aplicacao(existente.getId()));
        when(anuncioService.buscarPorIds(anyList())).thenReturn(List.of(existente));
        when(consultaDadosUsuario.buscarPorId(EMPREGADOR_ID)).thenReturn(Optional.of(dados("Maria")));

        assertThat(useCase.executar(PRESTADOR_ID, AbaCandidatura.PENDENTES, 0).getContent())
                .singleElement()
                .satisfies(item -> assertThat(item.anuncio()).isSameAs(existente));
    }

    @Test
    void dadoPaginaVazia_quandoLista_entaoNaoConsultaEmpregador() {
        pagina(3, 40);
        when(anuncioService.buscarPorIds(List.of())).thenReturn(List.of());

        Page<MinhaCandidatura> resultado = useCase.executar(PRESTADOR_ID, AbaCandidatura.PENDENTES, 3);

        assertThat(resultado.getContent()).isEmpty();
        assertThat(resultado.getNumber()).isEqualTo(3);
        assertThat(resultado.getTotalElements()).isEqualTo(40);
        verifyNoInteractions(consultaDadosUsuario);
    }
}
