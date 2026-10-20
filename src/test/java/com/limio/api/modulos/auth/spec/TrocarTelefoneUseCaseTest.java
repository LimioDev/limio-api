package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.actions.usecase.TrocarTelefoneUseCase;
import com.limio.api.modulos.auth.excecao.ContaSuspensaException;
import com.limio.api.modulos.auth.excecao.SenhaAtualIncorretaException;
import com.limio.api.modulos.auth.excecao.SessaoInvalidaException;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

@ExtendWith(MockitoExtension.class)
class TrocarTelefoneUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final Instant CONFIRMADO = Instant.parse("2026-10-01T10:00:00Z");

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private SenhaHasher senhaHasher;

    private TrocarTelefoneUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new TrocarTelefoneUseCase(usuarioService, senhaHasher);
    }

    private Usuario usuario(StatusConta status) {
        return Usuario.builder()
                .email("maria@example.com")
                .telefone("11999998888")
                .senhaHash("hash")
                .emailConfirmadoEm(CONFIRMADO)
                .telefoneConfirmadoEm(CONFIRMADO)
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(status)
                .build();
    }

    @Test
    void deveGravarONumeroELimparSoAConfirmacaoDoTelefone() {
        Usuario usuario = usuario(StatusConta.ATIVA);
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID)).thenReturn(usuario);
        when(senhaHasher.confere("senha", "hash")).thenReturn(true);
        when(usuarioService.salvar(usuario)).thenReturn(usuario);

        Usuario atualizado = useCase.executar(USUARIO_ID, "21988887777", "senha");

        assertThat(atualizado.getTelefone()).isEqualTo("21988887777");
        assertThat(atualizado.getTelefoneConfirmadoEm()).isNull();
        assertThat(atualizado.getEmailConfirmadoEm()).isEqualTo(CONFIRMADO);
        assertThat(atualizado.getEmail()).isEqualTo("maria@example.com");
        verify(usuarioService).salvar(usuario);
    }

    @Test
    void numeroIgualAoAtualNaoDeveFazerNada() {
        Usuario usuario = usuario(StatusConta.ATIVA);
        usuario.setTelefone("11999998888");
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID)).thenReturn(usuario);
        when(senhaHasher.confere("senha", "hash")).thenReturn(true);

        useCase.executar(USUARIO_ID, "11999998888", "senha");

        assertThat(usuario.getTelefoneConfirmadoEm()).isEqualTo(CONFIRMADO);
        verify(usuarioService, never()).salvar(any());
    }

    @Test
    void telefoneLegadoDeDezDigitosDevePoderSerTrocadoPorCelular() {
        Usuario usuario = usuario(StatusConta.ATIVA);
        usuario.setTelefone("1133334444");
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID)).thenReturn(usuario);
        when(senhaHasher.confere("senha", "hash")).thenReturn(true);
        when(usuarioService.salvar(usuario)).thenReturn(usuario);

        Usuario atualizado = useCase.executar(USUARIO_ID, "11999998888", "senha");

        assertThat(atualizado.getTelefone()).isEqualTo("11999998888");
        assertThat(atualizado.getTelefoneConfirmadoEm()).isNull();
    }

    @Test
    void senhaErradaNaoDeveGravar() {
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID)).thenReturn(usuario(StatusConta.ATIVA));
        when(senhaHasher.confere("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, "21988887777", "errada"))
                .isInstanceOf(SenhaAtualIncorretaException.class);

        verify(usuarioService, never()).salvar(any());
    }

    @Test
    void contaSuspensaNaoDeveTrocar() {
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID)).thenReturn(usuario(StatusConta.BLOQUEADA));

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, "21988887777", "senha"))
                .isInstanceOf(ContaSuspensaException.class);

        verify(usuarioService, never()).salvar(any());
    }

    @Test
    void contaEncerradaNaoDeveTrocar() {
        Usuario encerrada = usuario(StatusConta.ATIVA);
        encerrada.setAnonimizadoEm(CONFIRMADO);
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID)).thenReturn(encerrada);

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, "21988887777", "senha"))
                .isInstanceOf(SessaoInvalidaException.class);

        verify(usuarioService, never()).salvar(any());
    }
}
