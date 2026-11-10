package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.service.ValidacaoDoAnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.EditarAnuncioUseCase;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.excecao.AnuncioEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.CategoriaIndisponivelException;
import com.limio.api.modulos.anuncio.excecao.DataNoPassadoException;

@ExtendWith(MockitoExtension.class)
class EditarAnuncioUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID CATEGORIA_ANTIGA_ID = UUID.randomUUID();
    private static final UUID CATEGORIA_NOVA_ID = UUID.randomUUID();

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ConsultaCategoria consultaCategoria;

    @Mock
    private TravaTransacional trava;

    private EditarAnuncioUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new EditarAnuncioUseCase(anuncioService, aplicacaoService,
                new ValidacaoDoAnuncioService(consultaCategoria), trava);
    }

    private Anuncio anuncio(StatusAnuncio status) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .categoriaId(CATEGORIA_ANTIGA_ID)
                .descricao("Instalar 3 tomadas novas na sala")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(5)))
                .bairroRegiaoSnapshot("Pinheiros")
                .tamanhoLocal("Sala de 20m²")
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    private static Anuncio novosDados(Instant dataHora) {
        return Anuncio.builder()
                .categoriaId(CATEGORIA_NOVA_ID)
                .titulo("Instalar 5 tomadas")
                .descricao("Agora são 5 tomadas, na sala e no quarto")
                .dataHoraPretendida(dataHora)
                .numeroPessoas(2)
                .observacoes("Levar escada")
                .build();
    }

    private static Anuncio novosDados() {
        return novosDados(Instant.now().plus(Duration.ofDays(7)));
    }

    private void categoriaNovaAtiva(boolean ativa) {
        when(consultaCategoria.buscarPorId(CATEGORIA_NOVA_ID))
                .thenReturn(Optional.of(new ResumoCategoria(CATEGORIA_NOVA_ID, "Elétrica", "Reparos", ativa)));
    }

    @Test
    void dadoAnuncioPublicadoSemEscolhida_quandoEdita_entaoTrocaOsCamposEditaveisESalva() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        Anuncio novos = novosDados();
        categoriaNovaAtiva(true);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);
        when(anuncioService.salvar(anuncio)).thenReturn(anuncio);

        Anuncio editado = useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, novos);

        assertThat(editado.getCategoriaId()).isEqualTo(CATEGORIA_NOVA_ID);
        assertThat(editado.getTitulo()).isEqualTo("Instalar 5 tomadas");
        assertThat(editado.getDescricao()).isEqualTo("Agora são 5 tomadas, na sala e no quarto");
        assertThat(editado.getDataHoraPretendida()).isEqualTo(novos.getDataHoraPretendida());
        assertThat(editado.getNumeroPessoas()).isEqualTo(2);
        assertThat(editado.getObservacoes()).isEqualTo("Levar escada");
        // PUT substitui: opcional não enviado é limpo.
        assertThat(editado.getTamanhoLocal()).isNull();
        // O que não é editável fica como estava.
        assertThat(editado.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(editado.getContratanteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(editado.getBairroRegiaoSnapshot()).isEqualTo("Pinheiros");
        verify(anuncioService).salvar(anuncio);
    }

    @Test
    void dadoEdicao_quandoExecuta_entaoTravaOAnuncioAntesDeLerOEstado() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        categoriaNovaAtiva(true);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);
        when(anuncioService.salvar(anuncio)).thenReturn(anuncio);

        useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, novosDados());

        InOrder ordem = inOrder(trava, anuncioService, aplicacaoService);
        ordem.verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());
        ordem.verify(anuncioService).buscarPorId(ANUNCIO_ID);
        ordem.verify(aplicacaoService).existeEscolhida(ANUNCIO_ID);
        ordem.verify(anuncioService).salvar(anuncio);
    }

    @Test
    void dadoAnuncioPublicadoComCandidaturaEscolhida_quandoEdita_entaoRecusaESemTocarNoAnuncio() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        categoriaNovaAtiva(true);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, novosDados()))
                .isInstanceOf(AnuncioEstadoInvalidoException.class);

        assertThat(anuncio.getTitulo()).isEqualTo("Instalar 3 tomadas");
        verify(anuncioService, never()).salvar(any());
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = "PUBLICADO", mode = EnumSource.Mode.EXCLUDE)
    void dadoAnuncioForaDePublicado_quandoEdita_entaoRecusa(StatusAnuncio status) {
        Anuncio anuncio = anuncio(status);
        categoriaNovaAtiva(true);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, novosDados()))
                .isInstanceOf(AnuncioEstadoInvalidoException.class);

        assertThat(anuncio.getTitulo()).isEqualTo("Instalar 3 tomadas");
        verify(anuncioService, never()).salvar(any());
    }

    @Test
    void dadoAnuncioDeOutroContratante_quandoEdita_entao404() {
        categoriaNovaAtiva(true);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(StatusAnuncio.PUBLICADO)));

        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), ANUNCIO_ID, novosDados()))
                .isInstanceOf(AnuncioNaoEncontradoException.class);

        verifyNoInteractions(aplicacaoService);
        verify(anuncioService, never()).salvar(any());
    }

    @Test
    void dadoAnuncioInexistente_quandoEdita_entao404() {
        categoriaNovaAtiva(true);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, novosDados()))
                .isInstanceOf(AnuncioNaoEncontradoException.class);
    }

    @Test
    void dadoDataNoPassado_quandoEdita_entaoRecusaAntesDeTravar() {
        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID,
                novosDados(Instant.now().minus(Duration.ofMinutes(1)))))
                .isInstanceOf(DataNoPassadoException.class);

        verifyNoInteractions(trava, anuncioService, aplicacaoService);
    }

    @Test
    void dadoCategoriaInativa_quandoEdita_entaoRecusaAntesDeTravar() {
        categoriaNovaAtiva(false);

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, novosDados()))
                .isInstanceOf(CategoriaIndisponivelException.class);

        verifyNoInteractions(trava, anuncioService, aplicacaoService);
    }

    @Test
    void dadoCategoriaInexistente_quandoEdita_entaoRecusa() {
        when(consultaCategoria.buscarPorId(CATEGORIA_NOVA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(CONTRATANTE_ID, ANUNCIO_ID, novosDados()))
                .isInstanceOf(CategoriaIndisponivelException.class);
    }
}
