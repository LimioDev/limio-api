package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.MeuAnuncio;
import com.limio.api.modulos.anuncio.actions.usecase.ConsultarMeuAnuncioUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class ConsultarMeuAnuncioUseCaseTest {

    private static final UUID DONO_ID = UUID.randomUUID();
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CATEGORIA_ID = UUID.randomUUID();
    private static final ResumoCategoria CATEGORIA = new ResumoCategoria(CATEGORIA_ID, "Limpeza", "Limpeza", true);
    private static final LinkTemporario LINK = new LinkTemporario("https://bucket/assinado",
            Instant.parse("2026-10-27T18:00:00Z"));

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private MidiaService midiaService;

    @Mock
    private ArmazenamentoService armazenamentoService;

    @Mock
    private ConsultaCategoria consultaCategoria;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private ConsultaFotoPerfil consultaFotoPerfil;

    private ConsultarMeuAnuncioUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsultarMeuAnuncioUseCase(anuncioService, aplicacaoService, midiaService, armazenamentoService,
                consultaCategoria, consultaDadosUsuario, consultaFotoPerfil);
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(DONO_ID)
                .categoriaId(CATEGORIA_ID)
                .descricao("Faxina")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(2)))
                .status(StatusAnuncio.PUBLICADO)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
    }

    private void semFotosNemCandidatos() {
        when(consultaCategoria.buscarPorId(CATEGORIA_ID)).thenReturn(Optional.of(CATEGORIA));
        when(midiaService.buscarFotosAtivasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarEnviadasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of());
    }

    private Aplicacao enviada(UUID prestadorId) {
        return Aplicacao.builder().anuncioId(ANUNCIO_ID).prestadorId(prestadorId).status(StatusAplicacao.ENVIADA)
                .build();
    }

    private void prestador(UUID id, String nome) {
        when(consultaDadosUsuario.buscarPorId(id))
                .thenReturn(Optional.of(new DadosUsuario(nome, "p@example.com", null, null, null)));
    }

    @Test
    void dadoAnuncioDeOutraPessoa_quandoConsulta_entao404() {
        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), ANUNCIO_ID))
                .isInstanceOf(AnuncioNaoEncontradoException.class);
    }

    @Test
    void dadoAnuncioSemFotosNemCandidatos_quandoConsulta_entaoDevolveCategoriaEListasVazias() {
        semFotosNemCandidatos();

        MeuAnuncio meu = useCase.executar(DONO_ID, ANUNCIO_ID);

        assertThat(meu.anuncio().getId()).isEqualTo(ANUNCIO_ID);
        assertThat(meu.categoria()).isEqualTo(CATEGORIA);
        assertThat(meu.fotos()).isEmpty();
        assertThat(meu.candidatos()).isZero();
        assertThat(meu.primeirosCandidatos()).isEmpty();
    }

    @Test
    void dadoCincoCandidatos_quandoConsulta_entaoContaTodosEResumeOsQuatroPrimeiros() {
        semFotosNemCandidatos();
        UUID ana = UUID.randomUUID();
        UUID bruno = UUID.randomUUID();
        UUID carla = UUID.randomUUID();
        UUID diego = UUID.randomUUID();
        when(aplicacaoService.buscarEnviadasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of(enviada(ana), enviada(bruno),
                enviada(carla), enviada(diego), enviada(UUID.randomUUID())));
        prestador(ana, "Ana Lima");
        prestador(bruno, "Bruno da Costa");
        prestador(carla, "Carla");
        prestador(diego, "Diego Souza");
        when(consultaFotoPerfil.buscarFoto(ana)).thenReturn(Optional.of(LINK));

        MeuAnuncio meu = useCase.executar(DONO_ID, ANUNCIO_ID);

        assertThat(meu.candidatos()).isEqualTo(5);
        assertThat(meu.primeirosCandidatos()).extracting(candidato -> candidato.nome())
                .containsExactly("Ana L.", "Bruno C.", "Carla", "Diego S.");
        assertThat(meu.primeirosCandidatos().get(0).foto()).isEqualTo(LINK);
        assertThat(meu.primeirosCandidatos().get(1).foto()).isNull();
    }

    @Test
    void dadoCandidatoComContaEncerrada_quandoConsulta_entaoContaMasNaoResume() {
        semFotosNemCandidatos();
        UUID encerrado = UUID.randomUUID();
        when(aplicacaoService.buscarEnviadasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of(enviada(encerrado)));
        when(consultaDadosUsuario.buscarPorId(encerrado)).thenReturn(Optional.empty());

        MeuAnuncio meu = useCase.executar(DONO_ID, ANUNCIO_ID);

        assertThat(meu.candidatos()).isEqualTo(1);
        assertThat(meu.primeirosCandidatos()).isEmpty();
    }

    @Test
    void dadoStorageFora_quandoConsulta_entaoFotoSaiSemLinkEATelaNaoCai() {
        semFotosNemCandidatos();
        when(midiaService.buscarFotosAtivasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of(
                Midia.fotoAnuncio(DONO_ID, ANUNCIO_ID, "fotos-anuncio/a.jpg", "image/jpeg", 10, 10, 100)));
        when(armazenamentoService.gerarLink(anyString())).thenThrow(new ArmazenamentoIndisponivelException());

        MeuAnuncio meu = useCase.executar(DONO_ID, ANUNCIO_ID);

        assertThat(meu.fotos()).hasSize(1);
        assertThat(meu.fotos().get(0).link()).isNull();
    }
}
