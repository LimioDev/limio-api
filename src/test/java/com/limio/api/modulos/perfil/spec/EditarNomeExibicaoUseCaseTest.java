package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.modulos.perfil.actions.usecase.EditarNomeExibicaoUseCase;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class EditarNomeExibicaoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    private EditarNomeExibicaoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new EditarNomeExibicaoUseCase(perfilEmpregadorService);
    }

    @Test
    void deveGravarONovoNomeNoPerfilDoUsuario() {
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Maria").build();
        when(perfilEmpregadorService.buscarPorUsuarioIdOuFalhar(USUARIO_ID)).thenReturn(perfil);
        when(perfilEmpregadorService.salvar(perfil)).thenReturn(perfil);

        PerfilEmpregador atualizado = useCase.executar(USUARIO_ID, "Maria Silva");

        assertThat(atualizado.getNomeExibicao()).isEqualTo("Maria Silva");
        verify(perfilEmpregadorService).salvar(perfil);
    }

    @Test
    void naoDeveSalvarQuandoPerfilNaoExiste() {
        when(perfilEmpregadorService.buscarPorUsuarioIdOuFalhar(USUARIO_ID))
                .thenThrow(new PerfilEmpregadorNaoEncontradoException());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, "Maria Silva"))
                .isInstanceOf(PerfilEmpregadorNaoEncontradoException.class);

        verify(perfilEmpregadorService, never()).salvar(any());
    }
}
