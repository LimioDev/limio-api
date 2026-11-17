package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.eventos.NovoServicoNaAreaEvent;
import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaPrestadoresCompativeis;
import com.limio.api.comum.portas.PrestadorCompativel;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.AvisarPrestadoresCompativeisUseCase;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.eventos.AnuncioPublicadoEvent;
import com.limio.api.modulos.anuncio.excecao.CategoriaDoAnuncioNaoEncontradaException;

@ExtendWith(MockitoExtension.class)
class AvisarPrestadoresCompativeisUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID CATEGORIA_ID = UUID.randomUUID();
    private static final UUID RAFAEL = UUID.randomUUID();
    private static final UUID PEDRO = UUID.randomUUID();
    private static final double LATITUDE = -23.5372;
    private static final double LONGITUDE = -46.6815;

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private ConsultaPrestadoresCompativeis consultaPrestadoresCompativeis;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ConsultaCategoria consultaCategoria;

    @Mock
    private ApplicationEventPublisher eventos;

    private AvisarPrestadoresCompativeisUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new AvisarPrestadoresCompativeisUseCase(anuncioService, consultaPrestadoresCompativeis,
                consultaDadosUsuario, consultaCategoria, eventos);
    }

    private Anuncio anuncio(StatusAnuncio status, Double latitude, Double longitude) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Troca de chuveiro")
                .categoriaId(CATEGORIA_ID)
                .descricao("Trocar o chuveiro do banheiro")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(2)))
                .bairroRegiaoSnapshot("Perdizes")
                .enderecoCompletoSnapshot("Rua Cardoso de Almeida, 100, Perdizes, São Paulo - SP")
                .latitudeSnapshot(latitude)
                .longitudeSnapshot(longitude)
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    private void anuncioPublicado() {
        when(anuncioService.buscarPorId(ANUNCIO_ID))
                .thenReturn(Optional.of(anuncio(StatusAnuncio.PUBLICADO, LATITUDE, LONGITUDE)));
    }

    private void compativeis(PrestadorCompativel... prestadores) {
        when(consultaPrestadoresCompativeis.buscarCompativeis(CATEGORIA_ID, LATITUDE, LONGITUDE))
                .thenReturn(List.of(prestadores));
    }

    private void categoria() {
        when(consultaCategoria.buscarPorId(CATEGORIA_ID))
                .thenReturn(Optional.of(new ResumoCategoria(CATEGORIA_ID, "Elétrica", "Reparos", true)));
    }

    private void publicar() {
        useCase.aoPublicarAnuncio(new AnuncioPublicadoEvent(ANUNCIO_ID));
    }

    @Test
    void dadoPrestadorCompativelEApto_quandoAnuncioPublicado_entaoPedeAvisoSoComBairroEDistancia() {
        anuncioPublicado();
        compativeis(new PrestadorCompativel(RAFAEL, 3.4));
        when(consultaDadosUsuario.filtrarAptosAReceberAviso(List.of(RAFAEL))).thenReturn(Set.of(RAFAEL));
        categoria();

        publicar();

        ArgumentCaptor<NovoServicoNaAreaEvent> aviso = ArgumentCaptor.forClass(NovoServicoNaAreaEvent.class);
        verify(eventos).publishEvent(aviso.capture());
        assertThat(aviso.getValue().destinatarioId()).isEqualTo(RAFAEL);
        assertThat(aviso.getValue().anuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(aviso.getValue().anuncioTitulo()).isEqualTo("Troca de chuveiro");
        assertThat(aviso.getValue().categoriaNome()).isEqualTo("Elétrica");
        assertThat(aviso.getValue().bairroRegiao()).isEqualTo("Perdizes");
        assertThat(aviso.getValue().distanciaKm()).isEqualTo(3.4);
        assertThat(aviso.getValue().ocorridoEm()).isNotNull();
    }

    @Test
    void dadoVariosCompativeis_quandoAnuncioPublicado_entaoUmAvisoPorPrestadorApto() {
        anuncioPublicado();
        compativeis(new PrestadorCompativel(RAFAEL, 1.0), new PrestadorCompativel(PEDRO, 2.0));
        when(consultaDadosUsuario.filtrarAptosAReceberAviso(List.of(RAFAEL, PEDRO))).thenReturn(Set.of(PEDRO));
        categoria();

        publicar();

        ArgumentCaptor<NovoServicoNaAreaEvent> aviso = ArgumentCaptor.forClass(NovoServicoNaAreaEvent.class);
        verify(eventos).publishEvent(aviso.capture());
        assertThat(aviso.getAllValues()).extracting(NovoServicoNaAreaEvent::destinatarioId).containsExactly(PEDRO);
    }

    @Test
    void dadoDonoDoAnuncioNaListaDeCompativeis_quandoAnuncioPublicado_entaoDonoFicaDeFora() {
        anuncioPublicado();
        compativeis(new PrestadorCompativel(CONTRATANTE_ID, 0.0), new PrestadorCompativel(RAFAEL, 1.0));
        when(consultaDadosUsuario.filtrarAptosAReceberAviso(List.of(RAFAEL))).thenReturn(Set.of(RAFAEL));
        categoria();

        publicar();

        ArgumentCaptor<NovoServicoNaAreaEvent> aviso = ArgumentCaptor.forClass(NovoServicoNaAreaEvent.class);
        verify(eventos).publishEvent(aviso.capture());
        assertThat(aviso.getAllValues()).extracting(NovoServicoNaAreaEvent::destinatarioId).containsExactly(RAFAEL);
    }

    @Test
    void dadoNinguemNoRaio_quandoAnuncioPublicado_entaoNinguemEAvisadoNemConsultaContas() {
        anuncioPublicado();
        compativeis();

        publicar();

        verifyNoInteractions(consultaDadosUsuario, consultaCategoria, eventos);
    }

    @Test
    void dadoCategoriaDoAnuncioInexistente_quandoAnuncioPublicado_entaoFalhaComExcecaoDoModuloSemAvisar() {
        anuncioPublicado();
        compativeis(new PrestadorCompativel(RAFAEL, 1.0));
        when(consultaDadosUsuario.filtrarAptosAReceberAviso(List.of(RAFAEL))).thenReturn(Set.of(RAFAEL));
        when(consultaCategoria.buscarPorId(CATEGORIA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(this::publicar)
                .isInstanceOf(CategoriaDoAnuncioNaoEncontradaException.class)
                .hasMessageContaining(ANUNCIO_ID.toString());
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoAnuncioQueSaiuDePublicado_quandoEventoChega_entaoNaoAvisa() {
        when(anuncioService.buscarPorId(ANUNCIO_ID))
                .thenReturn(Optional.of(anuncio(StatusAnuncio.ENCERRADO, LATITUDE, LONGITUDE)));

        publicar();

        verifyNoInteractions(consultaPrestadoresCompativeis, eventos);
    }

    @Test
    void dadoAnuncioInexistente_quandoEventoChega_entaoNaoAvisa() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        publicar();

        verifyNoInteractions(consultaPrestadoresCompativeis, eventos);
    }

    @Test
    void dadoAnuncioSemCoordenadas_quandoEventoChega_entaoNaoAvisa() {
        when(anuncioService.buscarPorId(ANUNCIO_ID))
                .thenReturn(Optional.of(anuncio(StatusAnuncio.PUBLICADO, null, null)));

        publicar();

        verify(consultaPrestadoresCompativeis, never()).buscarCompativeis(any(), anyDouble(), anyDouble());
        verifyNoInteractions(eventos);
    }
}
