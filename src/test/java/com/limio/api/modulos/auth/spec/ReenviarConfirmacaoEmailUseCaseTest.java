package com.limio.api.modulos.auth.spec;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.modulos.auth.actions.usecase.ReenviarConfirmacaoEmailUseCase;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.verificacaocontato.EmissorConfirmacaoEmail;

@ExtendWith(MockitoExtension.class)
class ReenviarConfirmacaoEmailUseCaseTest {

    private static final String EMAIL = "maria@example.com";
    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private EmissorConfirmacaoEmail emissorConfirmacaoEmail;

    private ReenviarConfirmacaoEmailUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ReenviarConfirmacaoEmailUseCase(usuarioService, emissorConfirmacaoEmail);
    }

    private Usuario contaTravada() {
        Usuario usuario = Usuario.builder().email(EMAIL).build();
        ReflectionTestUtils.setField(usuario, "id", USUARIO_ID);
        when(usuarioService.buscarIdPorEmail(EMAIL)).thenReturn(Optional.of(USUARIO_ID));
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID)).thenReturn(usuario);
        lenient().when(emissorConfirmacaoEmail.limiteAtingido(eq(USUARIO_ID), any())).thenReturn(false);
        return usuario;
    }

    @Test
    void emailAindaNaoConfirmadoDeveReceberNovoLink() {
        Usuario usuario = contaTravada();

        useCase.executar(EMAIL);

        verify(emissorConfirmacaoEmail).emitir(eq(usuario), any(Instant.class));
    }

    @Test
    void emailInexistenteNaoFazNada() {
        when(usuarioService.buscarIdPorEmail(EMAIL)).thenReturn(Optional.empty());

        useCase.executar(EMAIL);

        verify(emissorConfirmacaoEmail, never()).emitir(any(), any());
    }

    @Test
    void emailJaConfirmadoNaoRecebeLink() {
        contaTravada().setEmailConfirmadoEm(Instant.now());

        useCase.executar(EMAIL);

        verify(emissorConfirmacaoEmail, never()).emitir(any(), any());
    }

    @Test
    void contaEncerradaNaoRecebeLink() {
        contaTravada().setAnonimizadoEm(Instant.now());

        useCase.executar(EMAIL);

        verify(emissorConfirmacaoEmail, never()).emitir(any(), any());
    }

    @Test
    void limiteBatidoNaoRecebeLinkNemRevelaNada() {
        contaTravada();
        when(emissorConfirmacaoEmail.limiteAtingido(eq(USUARIO_ID), any())).thenReturn(true);

        useCase.executar(EMAIL);

        verify(emissorConfirmacaoEmail, never()).emitir(any(), any());
    }

    /** Troca de e-mail concorrente: depois do lock a conta já tem outro endereço. */
    @Test
    void emailTrocadoAntesDoLockNaoRecebeLink() {
        contaTravada().setEmail("outro@example.com");

        useCase.executar(EMAIL);

        verify(emissorConfirmacaoEmail, never()).emitir(any(), any());
    }
}
