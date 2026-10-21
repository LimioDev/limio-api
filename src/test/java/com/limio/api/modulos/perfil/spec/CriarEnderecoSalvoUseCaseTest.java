package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.modulos.perfil.actions.usecase.CriarEnderecoSalvoUseCase;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class CriarEnderecoSalvoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID PERFIL_ID = UUID.randomUUID();

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    @Mock
    private EnderecoSalvoService enderecoSalvoService;

    private CriarEnderecoSalvoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new CriarEnderecoSalvoUseCase(perfilEmpregadorService, enderecoSalvoService);
    }

    private void perfilTravado() {
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Maria").build();
        ReflectionTestUtils.setField(perfil, "id", PERFIL_ID);
        when(perfilEmpregadorService.garantirPerfilParaAtualizar(USUARIO_ID)).thenReturn(perfil);
    }

    @Test
    void deveGravarOEnderecoNoPerfilDoUsuarioSemMexerNoPadraoQuandoNaoForPadrao() {
        perfilTravado();
        EnderecoSalvo novo = EnderecoSalvo.builder().apelido("Casa").padrao(false).build();
        when(enderecoSalvoService.salvar(novo)).thenReturn(novo);

        EnderecoSalvo criado = useCase.executar(USUARIO_ID, novo);

        assertThat(criado).isSameAs(novo);
        assertThat(criado.getPerfilEmpregadorId()).isEqualTo(PERFIL_ID);
        verify(enderecoSalvoService, never()).desmarcarEnderecoPadrao(PERFIL_ID);
    }

    @Test
    void deveDesmarcarOPadraoAnteriorAntesDeSalvarQuandoOnovoForPadrao() {
        perfilTravado();
        EnderecoSalvo novo = EnderecoSalvo.builder().apelido("Casa").padrao(true).build();
        when(enderecoSalvoService.salvar(novo)).thenReturn(novo);

        useCase.executar(USUARIO_ID, novo);

        InOrder ordem = inOrder(perfilEmpregadorService, enderecoSalvoService);
        ordem.verify(perfilEmpregadorService).garantirPerfilParaAtualizar(USUARIO_ID);
        ordem.verify(enderecoSalvoService).desmarcarEnderecoPadrao(PERFIL_ID);
        ordem.verify(enderecoSalvoService).salvar(novo);
    }

    @Test
    void naoDeveSalvarQuandoOPerfilNaoExisteOuAContaFoiEncerrada() {
        when(perfilEmpregadorService.garantirPerfilParaAtualizar(USUARIO_ID))
                .thenThrow(new PerfilEmpregadorNaoEncontradoException());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, EnderecoSalvo.builder().build()))
                .isInstanceOf(PerfilEmpregadorNaoEncontradoException.class);

        verifyNoInteractions(enderecoSalvoService);
    }
}
