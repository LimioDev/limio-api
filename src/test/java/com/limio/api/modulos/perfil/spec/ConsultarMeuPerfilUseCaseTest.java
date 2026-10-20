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

import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.perfil.actions.usecase.ConsultarMeuPerfilUseCase;
import com.limio.api.modulos.perfil.empregador.MeuPerfilEmpregador;
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
    void deveDevolverOPerfilGarantidoPeloService() {
        DadosUsuario dados = new DadosUsuario("Maria da Silva", "maria@example.com", "+5511999998888", null, null);
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Maria").build();
        MeuPerfilEmpregador meuPerfil = new MeuPerfilEmpregador(perfil, dados);
        when(perfilEmpregadorService.garantirPerfil(USUARIO_ID)).thenReturn(meuPerfil);

        assertThat(useCase.executar(USUARIO_ID)).isSameAs(meuPerfil);
    }

    @Test
    void deveRepassarOErroQuandoUsuarioNaoExisteOuFoiEncerrado() {
        when(perfilEmpregadorService.garantirPerfil(USUARIO_ID)).thenThrow(new PerfilEmpregadorNaoEncontradoException());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID))
                .isInstanceOf(PerfilEmpregadorNaoEncontradoException.class);
    }
}
