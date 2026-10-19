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

import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.perfil.actions.usecase.EditarNomeExibicaoUseCase;
import com.limio.api.modulos.perfil.empregador.MeuPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class EditarNomeExibicaoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final DadosUsuario DADOS = new DadosUsuario("Maria da Silva", "maria@example.com", "+5511999998888",
            null, null);

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    private EditarNomeExibicaoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new EditarNomeExibicaoUseCase(perfilEmpregadorService);
    }

    @Test
    void deveGravarONovoNomeNoPerfilGarantidoEDevolverOsMesmosDados() {
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Maria").build();
        when(perfilEmpregadorService.garantirPerfil(USUARIO_ID)).thenReturn(new MeuPerfilEmpregador(perfil, DADOS));
        when(perfilEmpregadorService.salvar(perfil)).thenReturn(perfil);

        MeuPerfilEmpregador resultado = useCase.executar(USUARIO_ID, "Maria Silva");

        assertThat(resultado.perfil().getNomeExibicao()).isEqualTo("Maria Silva");
        assertThat(resultado.dados()).isSameAs(DADOS);
        verify(perfilEmpregadorService).salvar(perfil);
    }

    @Test
    void naoDeveSalvarQuandoUsuarioNaoExisteOuFoiEncerrado() {
        when(perfilEmpregadorService.garantirPerfil(USUARIO_ID)).thenThrow(new PerfilEmpregadorNaoEncontradoException());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, "Maria Silva"))
                .isInstanceOf(PerfilEmpregadorNaoEncontradoException.class);

        verify(perfilEmpregadorService, never()).salvar(any());
    }
}
