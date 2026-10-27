package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.modulos.perfil.actions.service.Geocodificador;
import com.limio.api.modulos.perfil.actions.usecase.SalvarPerfilAtuacaoUseCase;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.categoria.CategoriaService;
import com.limio.api.modulos.perfil.excecao.CategoriaInvalidaException;
import com.limio.api.modulos.perfil.excecao.MunicipioNaoEncontradoException;
import com.limio.api.modulos.perfil.geo.Coordenadas;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.JanelaAtendimento;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.enums.DiaSemana;

@ExtendWith(MockitoExtension.class)
class SalvarPerfilAtuacaoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final Coordenadas CAMPINAS = new Coordenadas(-22.9056391, -47.059564);

    @Mock
    private PerfilPrestadorService perfilPrestadorService;

    @Mock
    private CategoriaService categoriaService;

    @Mock
    private Geocodificador geocodificador;

    private SalvarPerfilAtuacaoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new SalvarPerfilAtuacaoUseCase(perfilPrestadorService, categoriaService, geocodificador,
                TransactionOperations.withoutTransaction());
    }

    /** Id preenchido como se viesse do banco: {@code BaseEntity} compara por id. */
    private static Categoria categoria(String nome) {
        Categoria categoria = Categoria.builder().nome(nome).grupo("Casa e reparos").ordem(1).ativa(true).build();
        ReflectionTestUtils.setField(categoria, "id", UUID.randomUUID());
        return categoria;
    }

    private static List<JanelaAtendimento> manhaDeSegunda() {
        return List.of(JanelaAtendimento.builder()
                .diaSemana(DiaSemana.SEGUNDA)
                .horaInicio(LocalTime.of(8, 0))
                .horaFim(LocalTime.of(12, 0))
                .build());
    }

    private void campinasJaConhecida() {
        when(perfilPrestadorService.buscarCoordenadasConhecidas("Campinas", Uf.SP)).thenReturn(Optional.of(CAMPINAS));
    }

    private void semPerfilSalvo() {
        when(perfilPrestadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());
    }

    private void salvarDevolveOProprioPerfil() {
        when(perfilPrestadorService.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    @Test
    void deveCriarPerfilCompletoNoPrimeiroSalvamentoComCoordenadasDaGeocodificacao() {
        Categoria eletrica = categoria("Elétrica");
        when(categoriaService.buscarAtivasPorIds(Set.of(eletrica.getId()))).thenReturn(Set.of(eletrica));
        when(perfilPrestadorService.buscarCoordenadasConhecidas("Campinas", Uf.SP)).thenReturn(Optional.empty());
        when(geocodificador.localizarMunicipio("Campinas", Uf.SP)).thenReturn(Optional.of(CAMPINAS));
        semPerfilSalvo();
        salvarDevolveOProprioPerfil();

        PerfilPrestador salvo = useCase.executar(USUARIO_ID, Set.of(eletrica.getId()), 10, "Campinas", Uf.SP,
                manhaDeSegunda());

        assertThat(salvo.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(salvo.getEspecialidades()).containsExactly(eletrica);
        assertThat(salvo.getRaioAtendimentoKm()).isEqualTo(10);
        assertThat(salvo.getMunicipioBase()).isEqualTo("Campinas");
        assertThat(salvo.getUfBase()).isEqualTo(Uf.SP);
        assertThat(salvo.getCoordenadasBase()).isEqualTo(CAMPINAS);
        assertThat(salvo.getJanelas()).singleElement()
                .satisfies(janela -> assertThat(janela.getPerfilPrestador()).isSameAs(salvo));
        assertThat(salvo.isCompleto()).isTrue();
    }

    @Test
    void deveAceitarSemEspecialidadesComPerfilIncompleto() {
        when(categoriaService.buscarAtivasPorIds(Set.of())).thenReturn(Set.of());
        campinasJaConhecida();
        semPerfilSalvo();
        salvarDevolveOProprioPerfil();

        PerfilPrestador salvo = useCase.executar(USUARIO_ID, Set.of(), 10, "Campinas", Uf.SP, List.of());

        assertThat(salvo.getEspecialidades()).isEmpty();
        assertThat(salvo.isCompleto()).isFalse();
        verify(perfilPrestadorService).salvar(salvo);
    }

    @Test
    void deveReaproveitarCoordenadasJaConhecidasSemChamarAGeocodificacao() {
        when(categoriaService.buscarAtivasPorIds(Set.of())).thenReturn(Set.of());
        campinasJaConhecida();
        semPerfilSalvo();
        salvarDevolveOProprioPerfil();

        PerfilPrestador salvo = useCase.executar(USUARIO_ID, Set.of(), 10, "Campinas", Uf.SP, List.of());

        assertThat(salvo.getCoordenadasBase()).isEqualTo(CAMPINAS);
        verifyNoInteractions(geocodificador);
    }

    @Test
    void deveRecusarCategoriaInexistenteOuInativaSemGravarNada() {
        Categoria eletrica = categoria("Elétrica");
        Set<UUID> pedidas = Set.of(eletrica.getId(), UUID.randomUUID());
        when(categoriaService.buscarAtivasPorIds(pedidas)).thenReturn(Set.of(eletrica));

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, pedidas, 10, "Campinas", Uf.SP, List.of()))
                .isInstanceOf(CategoriaInvalidaException.class);

        verify(perfilPrestadorService, never()).salvar(any());
        verifyNoInteractions(geocodificador);
    }

    @Test
    void deveRecusarMunicipioQueAGeocodificacaoNaoAcha() {
        when(categoriaService.buscarAtivasPorIds(Set.of())).thenReturn(Set.of());
        when(perfilPrestadorService.buscarCoordenadasConhecidas("Campinass", Uf.SP)).thenReturn(Optional.empty());
        when(geocodificador.localizarMunicipio("Campinass", Uf.SP)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, Set.of(), 10, "Campinass", Uf.SP, List.of()))
                .isInstanceOf(MunicipioNaoEncontradoException.class);

        verify(perfilPrestadorService, never()).salvar(any());
    }

    @Test
    void deveSubstituirEspecialidadesEJanelasDoPerfilJaSalvo() {
        Categoria eletrica = categoria("Elétrica");
        Categoria pintura = categoria("Pintura");
        PerfilPrestador existente = PerfilPrestador.novo(USUARIO_ID);
        existente.atualizarAtuacao(Set.of(eletrica), 5, "Campinas", Uf.SP, CAMPINAS, manhaDeSegunda());
        when(categoriaService.buscarAtivasPorIds(Set.of(pintura.getId()))).thenReturn(Set.of(pintura));
        campinasJaConhecida();
        when(perfilPrestadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(existente));
        salvarDevolveOProprioPerfil();

        PerfilPrestador salvo = useCase.executar(USUARIO_ID, Set.of(pintura.getId()), 20, "Campinas", Uf.SP,
                List.of());

        assertThat(salvo).isSameAs(existente);
        assertThat(salvo.getEspecialidades()).containsExactly(pintura);
        assertThat(salvo.getJanelas()).isEmpty();
        assertThat(salvo.getRaioAtendimentoKm()).isEqualTo(20);
    }

    @Test
    void deveTravarPorUsuarioAntesDeLerOPerfil() {
        when(categoriaService.buscarAtivasPorIds(Set.of())).thenReturn(Set.of());
        campinasJaConhecida();
        semPerfilSalvo();
        salvarDevolveOProprioPerfil();

        useCase.executar(USUARIO_ID, Set.of(), 10, "Campinas", Uf.SP, List.of());

        InOrder ordem = inOrder(perfilPrestadorService);
        ordem.verify(perfilPrestadorService).travarPorUsuario(USUARIO_ID);
        ordem.verify(perfilPrestadorService).buscarPorUsuario(USUARIO_ID);
        ordem.verify(perfilPrestadorService).salvar(any());
    }
}
