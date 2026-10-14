package com.limio.api.modulos.auth.spec;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.actions.service.EmailService;
import com.limio.api.modulos.auth.actions.usecase.SolicitarRecuperacaoSenhaUseCase;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContato;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoService;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

@ExtendWith(MockitoExtension.class)
class SolicitarRecuperacaoSenhaUseCaseTest {

    private static final String EMAIL = "maria@example.com";
    private static final String FRONTEND_URL = "https://app.limio.test";

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private VerificacaoContatoService verificacaoContatoService;

    @Mock
    private EmailService emailService;

    private SolicitarRecuperacaoSenhaUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new SolicitarRecuperacaoSenhaUseCase(usuarioService, verificacaoContatoService, emailService,
                Duration.ofHours(1), 3, Duration.ofHours(1), FRONTEND_URL);
    }

    private Usuario usuario(StatusConta status) {
        return Usuario.builder()
                .email(EMAIL)
                .senhaHash("hash-bcrypt")
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(status)
                .build();
    }

    @Test
    void deveGerarTokenEEnviarEmailQuandoContaExiste() {
        Usuario usuario = usuario(StatusConta.ATIVA);
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(verificacaoContatoService.contarDesde(any(), eq(FinalidadeVerificacao.RECUPERACAO_SENHA), any()))
                .thenReturn(0L);

        useCase.executar(EMAIL);

        verify(verificacaoContatoService).salvar(any(VerificacaoContato.class));
        verify(emailService).enviarRecuperacaoSenha(eq(EMAIL), anyString());
    }

    @Test
    void naoDeveFazerNadaQuandoEmailNaoExiste() {
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.empty());

        useCase.executar(EMAIL);

        verifyNoInteractions(verificacaoContatoService, emailService);
    }

    @Test
    void naoDeveEnviarEmailParaContaEncerrada() {
        Usuario encerrada = usuario(StatusConta.ATIVA);
        encerrada.setAnonimizadoEm(Instant.now());
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(encerrada));

        useCase.executar(EMAIL);

        verifyNoInteractions(verificacaoContatoService, emailService);
    }

    @Test
    void naoDeveEnviarEmailQuandoLimiteDeSolicitacoesBateu() {
        Usuario usuario = usuario(StatusConta.ATIVA);
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(verificacaoContatoService.contarDesde(any(), eq(FinalidadeVerificacao.RECUPERACAO_SENHA), any()))
                .thenReturn(3L);

        useCase.executar(EMAIL);

        verify(verificacaoContatoService, never()).salvar(any());
        verifyNoInteractions(emailService);
    }
}
