package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.actions.usecase.TrocarPapelAtivoUseCase;
import com.limio.api.modulos.auth.excecao.ContaSuspensaException;
import com.limio.api.modulos.auth.excecao.PapelInvalidoException;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

@ExtendWith(MockitoExtension.class)
class TrocarPapelAtivoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private UsuarioService usuarioService;

    private TrocarPapelAtivoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new TrocarPapelAtivoUseCase(usuarioService);
    }

    private Usuario empregador(StatusConta status) {
        return Usuario.builder().papelAtivo(PapelUsuario.EMPREGADOR).statusConta(status).build();
    }

    @Test
    void deveGravarONovoPapelNoUsuario() {
        Usuario usuario = empregador(StatusConta.ATIVA);
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuario);
        when(usuarioService.salvar(usuario)).thenReturn(usuario);

        Usuario atualizado = useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR);

        assertThat(atualizado.getPapelAtivo()).isEqualTo(PapelUsuario.PRESTADOR);
        verify(usuarioService).salvar(usuario);
    }

    @Test
    void deveRecusarTrocaParaAdmin() {
        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.ADMIN))
                .isInstanceOf(PapelInvalidoException.class);

        verifyNoInteractions(usuarioService);
    }

    @Test
    void deveRecusarTrocaComContaSuspensa() {
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(empregador(StatusConta.BLOQUEADA));

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR))
                .isInstanceOf(ContaSuspensaException.class);

        verify(usuarioService, never()).salvar(any());
    }
}
