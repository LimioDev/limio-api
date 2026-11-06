package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.data.domain.Pageable;

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
import com.limio.api.modulos.anuncio.AnuncioDoMuralProjecao;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.CriterioDoMural;
import com.limio.api.modulos.anuncio.ItemDoMural;
import com.limio.api.modulos.anuncio.actions.usecase.ListarMuralUseCase;
import com.limio.api.modulos.anuncio.enums.QuandoMural;
import com.limio.api.modulos.anuncio.excecao.ContatosNaoConfirmadosException;
import com.limio.api.modulos.anuncio.excecao.PerfilIncompletoException;
import com.limio.api.modulos.anuncio.excecao.SomentePrestadorVeMuralException;

@ExtendWith(MockitoExtension.class)
class ListarMuralUseCaseTest {

    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID ELETRICA = UUID.randomUUID();
    private static final UUID OUTROS = UUID.randomUUID();
    private static final Instant CONFIRMADO = Instant.parse("2026-01-01T00:00:00Z");
    private static final ResumoPerfilPrestador PERFIL = new ResumoPerfilPrestador(Set.of(ELETRICA), OUTROS, 15, -22.9, -47.06);

    @Mock
    private AnuncioService anuncioService;
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

    private ListarMuralUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ListarMuralUseCase(anuncioService, consultaDadosUsuario, consultaPerfilPrestador,
                consultaCategoria, consultaPerfilEmpregador, midiaService, armazenamentoService);
    }

    private void prestadorHabilitado() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(
                new DadosUsuario("Rafael Souza", "r@example.com", "+5511999998888", CONFIRMADO, CONFIRMADO)));
        when(consultaPerfilPrestador.buscarCompleto(PRESTADOR_ID)).thenReturn(Optional.of(PERFIL));
    }

    private AnuncioDoMuralProjecao linha(UUID categoriaId, UUID contratanteId) {
        AnuncioDoMuralProjecao linha = org.mockito.Mockito.mock(AnuncioDoMuralProjecao.class);
        when(linha.getId()).thenReturn(UUID.randomUUID());
        when(linha.getTitulo()).thenReturn("Trocar chuveiro");
        when(linha.getCategoriaId()).thenReturn(categoriaId);
        when(linha.getContratanteId()).thenReturn(contratanteId);
        when(linha.getDataHoraPretendida()).thenReturn(Instant.parse("2026-11-05T13:00:00Z"));
        when(linha.getBairroRegiao()).thenReturn("Cambuí");
        when(linha.getDistanciaKm()).thenReturn(3.2);
        return linha;
    }

    private CriterioDoMural criterioEnviado() {
        ArgumentCaptor<CriterioDoMural> captor = ArgumentCaptor.forClass(CriterioDoMural.class);
        verify(anuncioService).buscarMural(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    @Test
    void dadoPapelEmpregador_quandoListaMural_entaoAcessoNegadoSemConsultarNada() {
        assertThatThrownBy(() -> useCase.executar(PRESTADOR_ID, PapelUsuario.EMPREGADOR, null, null, QuandoMural.QUALQUER, 0, 20))
                .isInstanceOf(SomentePrestadorVeMuralException.class);
        verifyNoInteractions(consultaDadosUsuario, consultaPerfilPrestador, anuncioService);
    }

    @Test
    void dadoEmailNaoConfirmado_quandoListaMural_entaoContatosNaoConfirmados() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(
                new DadosUsuario("Rafael", "r@example.com", "+5511999998888", null, CONFIRMADO)));

        assertThatThrownBy(() -> useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, null, null, QuandoMural.QUALQUER, 0, 20))
                .isInstanceOf(ContatosNaoConfirmadosException.class)
                .hasMessageContaining("ver os anúncios");
        verifyNoInteractions(anuncioService);
    }

    @Test
    void dadoTelefoneNaoConfirmado_quandoListaMural_entaoContatosNaoConfirmados() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(
                new DadosUsuario("Rafael", "r@example.com", "+5511999998888", CONFIRMADO, null)));

        assertThatThrownBy(() -> useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, null, null, QuandoMural.QUALQUER, 0, 20))
                .isInstanceOf(ContatosNaoConfirmadosException.class);
    }

    @Test
    void dadoContaEncerrada_quandoListaMural_entaoContatosNaoConfirmados() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, null, null, QuandoMural.QUALQUER, 0, 20))
                .isInstanceOf(ContatosNaoConfirmadosException.class);
    }

    @Test
    void dadoPerfilIncompleto_quandoListaMural_entaoPerfilIncompleto() {
        when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.of(
                new DadosUsuario("Rafael", "r@example.com", "+5511999998888", CONFIRMADO, CONFIRMADO)));
        when(consultaPerfilPrestador.buscarCompleto(PRESTADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, null, null, QuandoMural.QUALQUER, 0, 20))
                .isInstanceOf(PerfilIncompletoException.class);
        verifyNoInteractions(anuncioService);
    }

    @Test
    void dadoSemFiltros_quandoListaMural_entaoUsaACompatibilidadeDoPrestadorSemRecorte() {
        prestadorHabilitado();
        when(anuncioService.buscarMural(any(), any())).thenReturn(Page.empty());

        useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, null, null, QuandoMural.QUALQUER, 0, 20);

        CriterioDoMural criterio = criterioEnviado();
        assertThat(criterio.prestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(criterio.especialidadeIds()).containsExactly(ELETRICA);
        assertThat(criterio.categoriaOutrosId()).isEqualTo(OUTROS);
        assertThat(criterio.raioKm()).isEqualTo(15);
        assertThat(criterio.categoriasFiltro()).isEmpty();
        assertThat(criterio.ate()).isNull();
    }

    @Test
    void dadoRaioMaiorQueODoPrestador_quandoListaMural_entaoFicaNoRaioDoPrestador() {
        prestadorHabilitado();
        when(anuncioService.buscarMural(any(), any())).thenReturn(Page.empty());

        useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, null, 40, QuandoMural.QUALQUER, 0, 20);

        assertThat(criterioEnviado().raioKm()).isEqualTo(15);
    }

    @Test
    void dadoRaioMenorQueODoPrestador_quandoListaMural_entaoUsaORaioDoFiltro() {
        prestadorHabilitado();
        when(anuncioService.buscarMural(any(), any())).thenReturn(Page.empty());

        useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, null, 5, QuandoMural.QUALQUER, 0, 20);

        assertThat(criterioEnviado().raioKm()).isEqualTo(5);
    }

    @Test
    void dadoCategoriasEQuando_quandoListaMural_entaoRepassaFiltroEJanela() {
        prestadorHabilitado();
        when(anuncioService.buscarMural(any(), any())).thenReturn(Page.empty());

        useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, Set.of(ELETRICA), null, QuandoMural.ESTA_SEMANA, 2, 10);

        CriterioDoMural criterio = criterioEnviado();
        assertThat(criterio.categoriasFiltro()).containsExactly(ELETRICA);
        assertThat(criterio.ate()).isNotNull();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(anuncioService).buscarMural(any(), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(2, 10));
    }

    @Test
    void dadoPaginaVazia_quandoListaMural_entaoNaoFazConsultasEmLote() {
        prestadorHabilitado();
        when(anuncioService.buscarMural(any(), any())).thenReturn(Page.empty());

        Page<ItemDoMural> pagina = useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, null, null, QuandoMural.QUALQUER, 0, 20);

        assertThat(pagina.getContent()).isEmpty();
        verifyNoInteractions(consultaCategoria, consultaPerfilEmpregador, midiaService);
    }

    @Test
    void dadoAnunciosDoMesmoEmpregador_quandoListaMural_entaoMontaItensComUmaConsultaEmLoteDeCadaDado() {
        prestadorHabilitado();
        UUID empregador = UUID.randomUUID();
        AnuncioDoMuralProjecao comFoto = linha(ELETRICA, empregador);
        AnuncioDoMuralProjecao semFoto = linha(ELETRICA, empregador);
        when(anuncioService.buscarMural(any(), any())).thenReturn(new PageImpl<>(List.of(comFoto, semFoto), PageRequest.of(0, 2), 9));
        ResumoCategoria categoria = new ResumoCategoria(ELETRICA, "Elétrica", "Reparos", true);
        when(consultaCategoria.buscarPorIdsEmLote(anyCollection())).thenReturn(Map.of(ELETRICA, categoria));
        ResumoPerfilEmpregador perfilEmpregador = new ResumoPerfilEmpregador("Mari", null, null, null, 0);
        when(consultaPerfilEmpregador.buscarResumosEmLote(anyCollection())).thenReturn(Map.of(empregador, perfilEmpregador));
        UUID idComFoto = comFoto.getId();
        Midia foto = Midia.fotoAnuncio(empregador, idComFoto, "fotos-anuncio/a", "image/jpeg", 10, 10, 100);
        when(midiaService.buscarPrimeirasFotosDosAnuncios(anyCollection())).thenReturn(Map.of(idComFoto, foto));
        LinkTemporario link = new LinkTemporario("https://bucket/a", Instant.now());
        when(armazenamentoService.gerarLink("fotos-anuncio/a")).thenReturn(link);

        Page<ItemDoMural> pagina = useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, null, null, QuandoMural.QUALQUER, 0, 20);

        assertThat(pagina.getTotalElements()).isEqualTo(9);
        assertThat(pagina.getContent()).extracting(ItemDoMural::primeiraFoto).containsExactly(link, null);
        assertThat(pagina.getContent()).extracting(ItemDoMural::categoria).containsOnly(categoria);
        assertThat(pagina.getContent()).extracting(ItemDoMural::empregador).containsOnly(perfilEmpregador);
        assertThat(pagina.getContent().get(0).distanciaKm()).isEqualTo(3.2);
        verify(consultaCategoria).buscarPorIdsEmLote(Set.of(ELETRICA));
        verify(consultaPerfilEmpregador).buscarResumosEmLote(Set.of(empregador));
    }

    @Test
    void dadoStorageIndisponivel_quandoListaMural_entaoItemSegueSemFoto() {
        prestadorHabilitado();
        UUID empregador = UUID.randomUUID();
        AnuncioDoMuralProjecao linha = linha(ELETRICA, empregador);
        when(anuncioService.buscarMural(any(), any())).thenReturn(new PageImpl<>(List.of(linha), PageRequest.of(0, 20), 1));
        when(consultaCategoria.buscarPorIdsEmLote(anyCollection())).thenReturn(Map.of(ELETRICA, new ResumoCategoria(ELETRICA, "Elétrica", "Reparos", true)));
        when(consultaPerfilEmpregador.buscarResumosEmLote(anyCollection())).thenReturn(Map.of(empregador, new ResumoPerfilEmpregador("Mari", null, null, null, 0)));
        UUID idLinha = linha.getId();
        Midia foto = Midia.fotoAnuncio(empregador, idLinha, "fotos-anuncio/a", "image/jpeg", 10, 10, 100);
        when(midiaService.buscarPrimeirasFotosDosAnuncios(anyCollection())).thenReturn(Map.of(idLinha, foto));
        when(armazenamentoService.gerarLink("fotos-anuncio/a")).thenThrow(new ArmazenamentoIndisponivelException());

        Page<ItemDoMural> pagina = useCase.executar(PRESTADOR_ID, PapelUsuario.PRESTADOR, null, null, QuandoMural.QUALQUER, 0, 20);

        assertThat(pagina.getContent().get(0).primeiraFoto()).isNull();
    }
}
