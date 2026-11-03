package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.ConsultaPerfilPrestador;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.comum.portas.ResumoPerfilPrestador;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.DetalheDoMural;
import com.limio.api.modulos.anuncio.MinhaCandidatura;
import com.limio.api.modulos.anuncio.actions.usecase.ConsultarDetalheDoMuralUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.ContatosNaoConfirmadosException;
import com.limio.api.modulos.anuncio.excecao.PerfilIncompletoException;
import com.limio.api.modulos.anuncio.excecao.SomentePrestadorVeMuralException;

@ExtendWith(MockitoExtension.class)
class ConsultarDetalheDoMuralUseCaseTest {

    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID EMPREGADOR_ID = UUID.randomUUID();
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID ELETRICA = UUID.randomUUID();
    private static final Instant CONFIRMADO = Instant.parse("2026-01-01T00:00:00Z");
    private static final ResumoPerfilPrestador PERFIL = new ResumoPerfilPrestador(Set.of(ELETRICA), UUID.randomUUID(), 15,
            -22.9056, -47.0608);

    @Mock
    private AnuncioService anuncioService;
    @Mock
    private AplicacaoService aplicacaoService;
    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;
    @Mock
    private ConsultaPerfilPrestador consultaPerfilPrestador;
    @Mock
    private ConsultaCategoria consultaCategoria;
    @Mock
    private ConsultaPerfilEmpregador consultaPerfilEmpregador;
    @Mock
    private MidiaService midiaService;
    @Mock
    private ArmazenamentoService armazenamentoService;

    private ConsultarDetalheDoMuralUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsultarDetalheDoMuralUseCase(anuncioService, aplicacaoService, consultaDadosUsuario,
                consultaPerfilPrestador, consultaCategoria, consultaPerfilEmpregador, midiaService, armazenamentoService);
    }

    private void prestadorHabilitado() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(
                new DadosUsuario("Rafael", "r@example.com", "+5511999998888", CONFIRMADO, CONFIRMADO)));
        when(consultaPerfilPrestador.buscarCompleto(PRESTADOR_ID)).thenReturn(Optional.of(PERFIL));
    }

    private Anuncio anuncio(UUID contratante, StatusAnuncio status) {
        Anuncio anuncio = Anuncio.builder().contratanteId(contratante).titulo("Trocar chuveiro")
                .categoriaId(ELETRICA).descricao("Descrição completa").dataHoraPretendida(Instant.parse("2026-11-05T13:00:00Z"))
                .bairroRegiaoSnapshot("Cambuí").cidadeSnapshot("Campinas").ufSnapshot(Uf.SP)
                .enderecoCompletoSnapshot("Rua Secreta, 100, Cambuí, Campinas - SP")
                .latitudeSnapshot(-22.9056 + 0.0900).longitudeSnapshot(-47.0608).status(status).build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    private void montarRelacionados() {
        when(consultaCategoria.buscarPorId(ELETRICA)).thenReturn(Optional.of(new ResumoCategoria(ELETRICA, "Elétrica", "Casa e reparos", true)));
        when(consultaPerfilEmpregador.buscarResumosEmLote(anyCollection())).thenReturn(Map.of(EMPREGADOR_ID,
                new ResumoPerfilEmpregador("Mari", "https://bucket/mari", Instant.now(), new BigDecimal("4.5"), 8)));
        when(midiaService.buscarFotosAtivasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of());
    }

    private DetalheDoMural executar() {
        return useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, ANUNCIO_ID);
    }

    @Test
    void dadoPapelEmpregador_quandoDetalha_entaoAcessoNegado() {
        assertThatThrownBy(() -> useCase.executar(PRESTADOR_ID, PapelUsuario.EMPREGADOR, ANUNCIO_ID))
                .isInstanceOf(SomentePrestadorVeMuralException.class);
        verifyNoInteractions(anuncioService);
    }

    @Test
    void dadoContatoNaoConfirmado_quandoDetalha_entaoContatosNaoConfirmados() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(
                new DadosUsuario("Rafael", "r@example.com", "+5511999998888", CONFIRMADO, null)));

        assertThatThrownBy(this::executar).isInstanceOf(ContatosNaoConfirmadosException.class);
    }

    @Test
    void dadoPerfilIncompleto_quandoDetalha_entaoPerfilIncompleto() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(
                new DadosUsuario("Rafael", "r@example.com", "+5511999998888", CONFIRMADO, CONFIRMADO)));
        when(consultaPerfilPrestador.buscarCompleto(PRESTADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(this::executar).isInstanceOf(PerfilIncompletoException.class);
        verifyNoInteractions(anuncioService);
    }

    @Test
    void dadoAnuncioInexistente_quandoDetalha_entaoNaoEncontrado() {
        prestadorHabilitado();
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(this::executar).isInstanceOf(AnuncioNaoEncontradoException.class);
    }

    @Test
    void dadoAnuncioPublicadoSemCandidatura_quandoDetalha_entaoTrazOsDadosSemCandidaturaESemLocalExato() {
        prestadorHabilitado();
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(EMPREGADOR_ID, StatusAnuncio.PUBLICADO)));
        when(aplicacaoService.buscarDoPrestadorNoAnuncio(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(Optional.empty());
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(false);
        montarRelacionados();
        Midia foto1 = Midia.fotoAnuncio(EMPREGADOR_ID, ANUNCIO_ID, "fotos-anuncio/1", "image/jpeg", 10, 10, 100);
        Midia foto2 = Midia.fotoAnuncio(EMPREGADOR_ID, ANUNCIO_ID, "fotos-anuncio/2", "image/jpeg", 10, 10, 100);
        when(midiaService.buscarFotosAtivasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of(foto1, foto2));
        when(armazenamentoService.gerarLink("fotos-anuncio/1")).thenReturn(new LinkTemporario("https://bucket/1", Instant.now()));
        when(armazenamentoService.gerarLink("fotos-anuncio/2")).thenThrow(new ArmazenamentoIndisponivelException());

        DetalheDoMural detalhe = executar();

        assertThat(detalhe.titulo()).isEqualTo("Trocar chuveiro");
        assertThat(detalhe.descricao()).isEqualTo("Descrição completa");
        assertThat(detalhe.categoria().nome()).isEqualTo("Elétrica");
        assertThat(detalhe.bairroRegiao()).isEqualTo("Cambuí");
        assertThat(detalhe.cidade()).isEqualTo("Campinas");
        assertThat(detalhe.uf()).isEqualTo(Uf.SP);
        assertThat(detalhe.distanciaKm()).isBetween(9.9, 10.1);
        assertThat(detalhe.empregador().mediaAvaliacoes()).isEqualByComparingTo("4.5");
        assertThat(detalhe.empregador().qtdAvaliacoes()).isEqualTo(8);
        assertThat(detalhe.fotos()).hasSize(2);
        assertThat(detalhe.fotos().get(0).link().url()).isEqualTo("https://bucket/1");
        assertThat(detalhe.fotos().get(1).link()).isNull();
        assertThat(detalhe.minhaCandidatura()).isNull();
    }

    @Test
    void dadoCandidaturaDoPrestador_quandoDetalha_entaoDevolveASituacaoDela() {
        prestadorHabilitado();
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(EMPREGADOR_ID, StatusAnuncio.PUBLICADO)));
        Aplicacao minha = Aplicacao.builder().anuncioId(ANUNCIO_ID).prestadorId(PRESTADOR_ID)
                .status(StatusAplicacao.ENVIADA).build();
        UUID minhaId = UUID.randomUUID();
        ReflectionTestUtils.setField(minha, "id", minhaId);
        when(aplicacaoService.buscarDoPrestadorNoAnuncio(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(Optional.of(minha));
        montarRelacionados();

        assertThat(executar().minhaCandidatura()).isEqualTo(new MinhaCandidatura(minhaId, StatusAplicacao.ENVIADA));
    }

    @Test
    void dadoCandidaturaEmAnuncioJaEncerrado_quandoDetalha_entaoAindaVeParaAcompanhar() {
        prestadorHabilitado();
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(EMPREGADOR_ID, StatusAnuncio.ENCERRADO)));
        when(aplicacaoService.buscarDoPrestadorNoAnuncio(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(Optional.of(
                Aplicacao.builder().anuncioId(ANUNCIO_ID).prestadorId(PRESTADOR_ID).status(StatusAplicacao.ENCERRADA).build()));
        montarRelacionados();

        assertThat(executar().minhaCandidatura().status()).isEqualTo(StatusAplicacao.ENCERRADA);
    }

    @Test
    void dadoAnuncioDoProprioPrestador_quandoDetalha_entaoNaoEncontrado() {
        prestadorHabilitado();
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(PRESTADOR_ID, StatusAnuncio.PUBLICADO)));
        when(aplicacaoService.buscarDoPrestadorNoAnuncio(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(this::executar).isInstanceOf(AnuncioNaoEncontradoException.class);
    }

    @Test
    void dadoAnuncioNaoPublicadoSemCandidatura_quandoDetalha_entaoNaoEncontrado() {
        prestadorHabilitado();
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(EMPREGADOR_ID, StatusAnuncio.ENCERRADO)));
        when(aplicacaoService.buscarDoPrestadorNoAnuncio(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(this::executar).isInstanceOf(AnuncioNaoEncontradoException.class);
    }

    @Test
    void dadoAnuncioComEscolhidoSemCandidaturaDoPrestador_quandoDetalha_entaoNaoEncontrado() {
        prestadorHabilitado();
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio(EMPREGADOR_ID, StatusAnuncio.PUBLICADO)));
        when(aplicacaoService.buscarDoPrestadorNoAnuncio(ANUNCIO_ID, PRESTADOR_ID)).thenReturn(Optional.empty());
        when(aplicacaoService.existeEscolhida(ANUNCIO_ID)).thenReturn(true);

        assertThatThrownBy(this::executar).isInstanceOf(AnuncioNaoEncontradoException.class);
    }
}
