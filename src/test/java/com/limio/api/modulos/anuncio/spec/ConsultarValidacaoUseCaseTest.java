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
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.ValidacaoDoServico;
import com.limio.api.modulos.anuncio.actions.usecase.ConsultarValidacaoUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class ConsultarValidacaoUseCaseTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
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
    private ConsultaDadosUsuario consultaDadosUsuario;

    private ConsultarValidacaoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsultarValidacaoUseCase(anuncioService, aplicacaoService, midiaService, armazenamentoService,
                consultaDadosUsuario);
    }

    private Anuncio anuncio(StatusAnuncio status) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .descricao("Instalar 3 tomadas novas na sala")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(5)))
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    private Aplicacao escolhida() {
        return Aplicacao.builder()
                .anuncioId(ANUNCIO_ID)
                .prestadorId(PRESTADOR_ID)
                .status(StatusAplicacao.ESCOLHIDA)
                .dataAplicacao(Instant.now())
                .escolhidaEm(Instant.now())
                .build();
    }

    @Test
    void dadoCheckoutComFotos_quandoConsulta_entaoDevolveFotosENomeDoPrestador() {
        Anuncio anuncio = anuncio(StatusAnuncio.AGUARDANDO_VALIDACAO);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));
        when(midiaService.buscarFotosAtivasDoCheckout(ANUNCIO_ID)).thenReturn(List.of(
                Midia.fotoAnuncio(CONTRATANTE_ID, ANUNCIO_ID, "fotos-checkout/a.jpg", "image/jpeg", 10, 10, 100)));
        when(armazenamentoService.gerarLinkSeDisponivel(anyString())).thenReturn(Optional.of(LINK));
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID))
                .thenReturn(Optional.of(new DadosUsuario("Carlos Souza", "carlos@example.com", "+5511988887777",
                        Instant.now(), Instant.now())));

        ValidacaoDoServico validacao = useCase.executar(ANUNCIO_ID, CONTRATANTE_ID);

        assertThat(validacao.fotosCheckout()).hasSize(1);
        assertThat(validacao.fotosCheckout().get(0).link()).isEqualTo(LINK);
        assertThat(validacao.nomePrestador()).isEqualTo("Carlos Souza");
    }

    @Test
    void dadoSemFotosDeCheckout_quandoConsulta_entaoDevolveListaVazia() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida()));
        when(midiaService.buscarFotosAtivasDoCheckout(ANUNCIO_ID)).thenReturn(List.of());
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID))
                .thenReturn(Optional.of(new DadosUsuario("Carlos Souza", "carlos@example.com", "+5511988887777",
                        Instant.now(), Instant.now())));

        ValidacaoDoServico validacao = useCase.executar(ANUNCIO_ID, CONTRATANTE_ID);

        assertThat(validacao.fotosCheckout()).isEmpty();
        assertThat(validacao.anuncio().getCheckoutEm()).isNull();
    }

    @Test
    void dadoQuemNaoEDono_quandoConsulta_entao404() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, UUID.randomUUID()))
                .isInstanceOf(ServicoNaoEncontradoException.class);
    }

    @Test
    void dadoAnuncioInexistente_quandoConsulta_entao404() {
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(ANUNCIO_ID, CONTRATANTE_ID))
                .isInstanceOf(ServicoNaoEncontradoException.class);
    }
}
