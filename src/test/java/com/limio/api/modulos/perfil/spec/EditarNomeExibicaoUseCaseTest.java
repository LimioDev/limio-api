package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.perfil.actions.usecase.EditarNomeExibicaoUseCase;
import com.limio.api.modulos.perfil.empregador.MeuPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class EditarNomeExibicaoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final DadosUsuario DADOS = new DadosUsuario("Maria da Silva", "maria@example.com", "11999998888",
            null, null);

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    private EditarNomeExibicaoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new EditarNomeExibicaoUseCase(consultaDadosUsuario, perfilEmpregadorService);
    }

    @Test
    void deveGravarONovoNomeNoPerfilDoUsuario() {
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Maria").build();
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(DADOS));
        when(perfilEmpregadorService.buscarOuCriar(USUARIO_ID, "Maria da Silva")).thenReturn(perfil);
        when(perfilEmpregadorService.salvar(perfil)).thenReturn(perfil);

        MeuPerfilEmpregador resultado = useCase.executar(USUARIO_ID, "Maria Silva");

        assertThat(resultado.perfil().getNomeExibicao()).isEqualTo("Maria Silva");
        assertThat(resultado.dados()).isSameAs(DADOS);
        verify(perfilEmpregadorService).salvar(perfil);
    }

    @Test
    void deveCriarOPerfilEGravarONomePedidoQuandoAindaNaoExiste() {
        PerfilEmpregador recemCriado = PerfilEmpregador.builder().usuarioId(USUARIO_ID)
                .nomeExibicao("Maria da Silva").build();
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(DADOS));
        when(perfilEmpregadorService.buscarOuCriar(USUARIO_ID, "Maria da Silva")).thenReturn(recemCriado);
        when(perfilEmpregadorService.salvar(recemCriado)).thenReturn(recemCriado);

        MeuPerfilEmpregador resultado = useCase.executar(USUARIO_ID, "Mari");

        assertThat(resultado.perfil().getNomeExibicao()).isEqualTo("Mari");
    }

    @Test
    void naoDeveCriarNemSalvarQuandoUsuarioNaoExisteOuFoiEncerrado() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, "Maria Silva"))
                .isInstanceOf(PerfilEmpregadorNaoEncontradoException.class);

        verifyNoInteractions(perfilEmpregadorService);
    }
}
