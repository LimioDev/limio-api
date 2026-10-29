package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
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
import com.limio.api.modulos.perfil.actions.usecase.CriarEnderecoSalvoUseCase;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;
import com.limio.api.modulos.perfil.excecao.EnderecoNaoLocalizadoException;
import com.limio.api.modulos.perfil.excecao.GeocodificacaoIndisponivelException;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;
import com.limio.api.modulos.perfil.geo.Coordenadas;

@ExtendWith(MockitoExtension.class)
class CriarEnderecoSalvoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID PERFIL_ID = UUID.randomUUID();

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    @Mock
    private EnderecoSalvoService enderecoSalvoService;

    @Mock
    private Geocodificador geocodificador;

    private CriarEnderecoSalvoUseCase useCase;

    private static final Coordenadas PONTO = new Coordenadas(-23.5614, -46.6559);

    @BeforeEach
    void montarUseCase() {
        useCase = new CriarEnderecoSalvoUseCase(perfilEmpregadorService, enderecoSalvoService, geocodificador,
                TransactionOperations.withoutTransaction());
    }

    private static EnderecoSalvo novo(boolean padrao) {
        return EnderecoSalvo.builder().apelido("Casa").rua("Avenida Paulista").numero("1000")
                .cidade("São Paulo").uf(Uf.SP).padrao(padrao).build();
    }

    private void geocodificadorAcha() {
        when(geocodificador.localizarEndereco(any())).thenReturn(Optional.of(PONTO));
    }

    private void perfilTravado() {
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Maria").build();
        ReflectionTestUtils.setField(perfil, "id", PERFIL_ID);
        when(perfilEmpregadorService.garantirPerfilParaAtualizar(USUARIO_ID)).thenReturn(perfil);
    }

    @Test
    void deveGravarOEnderecoNoPerfilDoUsuarioSemMexerNoPadraoQuandoNaoForPadrao() {
        perfilTravado();
        geocodificadorAcha();
        EnderecoSalvo novo = novo(false);
        when(enderecoSalvoService.salvar(novo)).thenReturn(novo);

        EnderecoSalvo criado = useCase.executar(USUARIO_ID, novo);

        assertThat(criado).isSameAs(novo);
        assertThat(criado.getPerfilEmpregadorId()).isEqualTo(PERFIL_ID);
        assertThat(criado.getLatitude()).isEqualTo(-23.5614);
        assertThat(criado.getLongitude()).isEqualTo(-46.6559);
        verify(enderecoSalvoService, never()).desmarcarEnderecoPadrao(PERFIL_ID);
    }

    @Test
    void deveDesmarcarOPadraoAnteriorAntesDeSalvarQuandoOnovoForPadrao() {
        perfilTravado();
        geocodificadorAcha();
        EnderecoSalvo novo = novo(true);
        when(enderecoSalvoService.salvar(novo)).thenReturn(novo);

        useCase.executar(USUARIO_ID, novo);

        InOrder ordem = inOrder(geocodificador, perfilEmpregadorService, enderecoSalvoService);
        ordem.verify(geocodificador).localizarEndereco(any());
        ordem.verify(perfilEmpregadorService).garantirPerfilParaAtualizar(USUARIO_ID);
        ordem.verify(enderecoSalvoService).desmarcarEnderecoPadrao(PERFIL_ID);
        ordem.verify(enderecoSalvoService).salvar(novo);
    }

    @Test
    void naoDeveSalvarQuandoOPerfilNaoExisteOuAContaFoiEncerrada() {
        geocodificadorAcha();
        when(perfilEmpregadorService.garantirPerfilParaAtualizar(USUARIO_ID))
                .thenThrow(new PerfilEmpregadorNaoEncontradoException());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, novo(false)))
                .isInstanceOf(PerfilEmpregadorNaoEncontradoException.class);

        verifyNoInteractions(enderecoSalvoService);
    }

    @Test
    void naoDeveAbrirTransacaoNemGravarQuandoOEnderecoNaoForLocalizado() {
        when(geocodificador.localizarEndereco(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, novo(true)))
                .isInstanceOf(EnderecoNaoLocalizadoException.class);

        verifyNoInteractions(perfilEmpregadorService, enderecoSalvoService);
    }

    @Test
    void naoDeveGravarQuandoAGeocodificacaoEstiverIndisponivel() {
        when(geocodificador.localizarEndereco(any())).thenThrow(new GeocodificacaoIndisponivelException());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, novo(false)))
                .isInstanceOf(GeocodificacaoIndisponivelException.class);

        verifyNoInteractions(perfilEmpregadorService, enderecoSalvoService);
    }
}
