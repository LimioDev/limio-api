package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.modulos.perfil.actions.usecase.ConsultarMeuPerfilUseCase;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class ConsultarMeuPerfilUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    private ConsultarMeuPerfilUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsultarMeuPerfilUseCase(perfilEmpregadorService);
    }

    @Test
    void deveDevolverOPerfilDoUsuario() {
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Maria").build();
        when(perfilEmpregadorService.buscarPorUsuarioIdOuFalhar(USUARIO_ID)).thenReturn(perfil);

        assertThat(useCase.executar(USUARIO_ID)).isSameAs(perfil);
    }

    @Test
    void devePropagarPerfilNaoEncontrado() {
        when(perfilEmpregadorService.buscarPorUsuarioIdOuFalhar(USUARIO_ID))
                .thenThrow(new PerfilEmpregadorNaoEncontradoException());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID))
                .isInstanceOf(PerfilEmpregadorNaoEncontradoException.class);
    }
}
