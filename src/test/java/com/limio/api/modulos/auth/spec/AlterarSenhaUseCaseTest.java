package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.actions.helper.RefreshTokenHelper;
import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.actions.service.EmailService;
import com.limio.api.modulos.auth.actions.service.TokenService;
import com.limio.api.modulos.auth.actions.usecase.AlterarSenhaUseCase;
import com.limio.api.modulos.auth.excecao.SenhaAtualIncorretaException;
import com.limio.api.modulos.auth.excecao.SenhaIgualAnteriorException;
import com.limio.api.modulos.auth.excecao.SessaoInvalidaException;
import com.limio.api.modulos.auth.sessao.Sessao;
import com.limio.api.modulos.auth.sessao.SessaoService;
import com.limio.api.modulos.auth.sessao.TokensSessao;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

@ExtendWith(MockitoExtension.class)
class AlterarSenhaUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final String SENHA_ATUAL = "senhaAtual123";
    private static final String NOVA_SENHA = "senhaNova123";
    private static final String REFRESH_TOKEN = "refresh-token-do-celular";
    private static final String FRONTEND_URL = "https://app.limio.test";

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private SessaoService sessaoService;

    @Mock
    private SenhaHasher senhaHasher;

    @Mock
    private TokenService tokenService;

    @Mock
    private EmailService emailService;

    private AlterarSenhaUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new AlterarSenhaUseCase(usuarioService, sessaoService, senhaHasher, tokenService, emailService,
                FRONTEND_URL);
    }

    private Usuario usuario() {
        Usuario usuario = Usuario.builder()
                .email("maria@example.com")
                .senhaHash("hash-antigo")
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(StatusConta.ATIVA)
                .build();
        ReflectionTestUtils.setField(usuario, "id", USUARIO_ID);
        return usuario;
    }

    private Sessao sessaoAtual() {
        return Sessao.builder()
                .refreshTokenHash(RefreshTokenHelper.hash(REFRESH_TOKEN))
                .expiraEm(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void deveAlterarSenhaManterSessaoAtualEEncerrarAsDemais() {
        Usuario usuario = usuario();
        Sessao sessaoAtual = sessaoAtual();
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuario);
        when(senhaHasher.confere(SENHA_ATUAL, "hash-antigo")).thenReturn(true);
        when(senhaHasher.confere(NOVA_SENHA, "hash-antigo")).thenReturn(false);
        when(senhaHasher.hash(NOVA_SENHA)).thenReturn("hash-novo");
        when(sessaoService.buscarDoUsuarioPorRefreshTokenHash(USUARIO_ID, RefreshTokenHelper.hash(REFRESH_TOKEN)))
                .thenReturn(Optional.of(sessaoAtual));
        var tokensNovos = new TokensSessao("jwt-novo", Instant.now(), "refresh-novo", Instant.now(), PapelUsuario.EMPREGADOR);
        when(tokenService.emitir(eq(usuario), any(Instant.class))).thenReturn(tokensNovos);

        TokensSessao tokens = useCase.executar(USUARIO_ID, SENHA_ATUAL, NOVA_SENHA, REFRESH_TOKEN);

        assertThat(tokens).isSameAs(tokensNovos);
        assertThat(usuario.getSenhaHash()).isEqualTo("hash-novo");
        verify(sessaoService).salvar(sessaoAtual);
        verify(sessaoService).revogarTodas(eq(USUARIO_ID), eq(sessaoAtual.getId()), any(Instant.class));
        verify(emailService).enviarSenhaAlterada(eq("maria@example.com"), any());
    }

    @Test
    void deveRecusarSenhaAtualIncorreta() {
        Usuario usuario = usuario();
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuario);
        when(senhaHasher.confere(SENHA_ATUAL, "hash-antigo")).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, SENHA_ATUAL, NOVA_SENHA, REFRESH_TOKEN))
                .isInstanceOf(SenhaAtualIncorretaException.class);

        verify(sessaoService, never()).salvar(any());
        verify(emailService, never()).enviarSenhaAlterada(any(), any());
    }

    @Test
    void deveRecusarNovaSenhaIgualAAtual() {
        Usuario usuario = usuario();
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuario);
        when(senhaHasher.confere(SENHA_ATUAL, "hash-antigo")).thenReturn(true);
        when(senhaHasher.confere(NOVA_SENHA, "hash-antigo")).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, SENHA_ATUAL, NOVA_SENHA, REFRESH_TOKEN))
                .isInstanceOf(SenhaIgualAnteriorException.class);
    }

    @Test
    void deveRecusarRefreshTokenQueNaoEDesteUsuario() {
        Usuario usuario = usuario();
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuario);
        when(senhaHasher.confere(SENHA_ATUAL, "hash-antigo")).thenReturn(true);
        when(senhaHasher.confere(NOVA_SENHA, "hash-antigo")).thenReturn(false);
        when(sessaoService.buscarDoUsuarioPorRefreshTokenHash(USUARIO_ID, RefreshTokenHelper.hash(REFRESH_TOKEN)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, SENHA_ATUAL, NOVA_SENHA, REFRESH_TOKEN))
                .isInstanceOf(SessaoInvalidaException.class);

        verify(usuarioService, never()).salvar(any());
    }

    @Test
    void deveRecusarContaSuspensa() {
        Usuario suspensa = usuario();
        suspensa.setStatusConta(StatusConta.BLOQUEADA);
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(suspensa);

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, SENHA_ATUAL, NOVA_SENHA, REFRESH_TOKEN))
                .isInstanceOf(com.limio.api.modulos.auth.excecao.ContaSuspensaException.class);

        verify(senhaHasher, never()).confere(any(), any());
    }
}
