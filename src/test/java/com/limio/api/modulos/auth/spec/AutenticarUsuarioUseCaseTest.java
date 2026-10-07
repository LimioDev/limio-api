package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.actions.helper.LimiteTentativasLogin;
import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.actions.service.TokenService;
import com.limio.api.modulos.auth.actions.usecase.AutenticarUsuarioUseCase;
import com.limio.api.modulos.auth.excecao.ContaSuspensaException;
import com.limio.api.modulos.auth.excecao.CredenciaisInvalidasException;
import com.limio.api.modulos.auth.excecao.LoginTemporariamenteBloqueadoException;
import com.limio.api.modulos.auth.sessao.Sessao;
import com.limio.api.modulos.auth.sessao.SessaoService;
import com.limio.api.modulos.auth.sessao.TokensSessao;
import com.limio.api.modulos.auth.tentativalogin.TentativaLogin;
import com.limio.api.modulos.auth.tentativalogin.TentativaLoginService;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

@ExtendWith(MockitoExtension.class)
class AutenticarUsuarioUseCaseTest {

    private static final String EMAIL = "maria@example.com";
    private static final String SENHA = "senhaForte123";
    private static final String DISPOSITIVO = "iPhone da Maria";
    private static final String IP = "203.0.113.10";

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private SessaoService sessaoService;

    @Mock
    private TentativaLoginService tentativaLoginService;

    @Mock
    private SenhaHasher senhaHasher;

    @Mock
    private TokenService tokenService;

    private final TentativaLogin tentativa = TentativaLogin.builder().email(EMAIL).ip(IP).build();

    private AutenticarUsuarioUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        var limite = new LimiteTentativasLogin(5, Duration.ofMinutes(15), Duration.ofMinutes(15));
        useCase = new AutenticarUsuarioUseCase(usuarioService, sessaoService, tentativaLoginService, limite,
                senhaHasher, tokenService);
    }

    private Usuario usuario(StatusConta status) {
        return Usuario.builder()
                .email(EMAIL)
                .senhaHash("hash-bcrypt")
                .papelAtivo(PapelUsuario.PRESTADOR)
                .statusConta(status)
                .build();
    }

    private void tentativaLiberada() {
        when(tentativaLoginService.registrarSeLiberado(anyString(), eq(IP), anyInt(), any()))
                .thenReturn(Optional.of(tentativa));
    }

    @Test
    void deveAbrirSessaoDoAparelhoEApagarATentativaQuandoASenhaConfere() {
        Usuario usuario = usuario(StatusConta.ATIVA);
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario));
        tentativaLiberada();
        when(senhaHasher.confere(SENHA, "hash-bcrypt")).thenReturn(true);
        var emitidos = new TokensSessao("jwt", Instant.now(), "refresh", PapelUsuario.PRESTADOR);
        when(tokenService.emitir(any(Sessao.class), any(Instant.class))).thenReturn(emitidos);

        TokensSessao tokens = useCase.executar(EMAIL, SENHA, DISPOSITIVO, IP);

        assertThat(tokens).isSameAs(emitidos);
        ArgumentCaptor<Sessao> sessao = ArgumentCaptor.forClass(Sessao.class);
        verify(sessaoService).salvar(sessao.capture());
        assertThat(sessao.getValue().getUsuario()).isSameAs(usuario);
        assertThat(sessao.getValue().getDispositivo()).isEqualTo(DISPOSITIVO);
        assertThat(sessao.getValue().getIp()).isEqualTo(IP);
        verify(tentativaLoginService).remover(tentativa);
    }

    @Test
    void deveAutenticarContaAindaPendenteDeVerificacao() {
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario(StatusConta.PENDENTE_VERIFICACAO)));
        tentativaLiberada();
        when(senhaHasher.confere(SENHA, "hash-bcrypt")).thenReturn(true);
        when(tokenService.emitir(any(Sessao.class), any(Instant.class)))
                .thenReturn(new TokensSessao("jwt", Instant.now(), "refresh", PapelUsuario.PRESTADOR));

        assertThat(useCase.executar(EMAIL, SENHA, DISPOSITIVO, IP)).isNotNull();
    }

    @Test
    void deveRecusarSenhaErradaMantendoATentativaComoFalha() {
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario(StatusConta.ATIVA)));
        tentativaLiberada();
        when(senhaHasher.confere(SENHA, "hash-bcrypt")).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(EMAIL, SENHA, DISPOSITIVO, IP))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(tentativaLoginService, never()).remover(any());
        verifyNoInteractions(tokenService, sessaoService);
    }

    @Test
    void deveResponderEmailInexistenteIgualSenhaErradaGastandoBcryptMesmoAssim() {
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.empty());
        tentativaLiberada();

        assertThatThrownBy(() -> useCase.executar(EMAIL, SENHA, DISPOSITIVO, IP))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(senhaHasher).simularConferencia(SENHA);
        verify(tentativaLoginService).registrarSeLiberado(eq(EMAIL), eq(IP), eq(5), any());
        verify(tentativaLoginService, never()).remover(any());
        verifyNoInteractions(tokenService, sessaoService);
    }

    @Test
    void deveContarAsTentativasPeloEmailGravadoNaContaNaoPeloDigitado() {
        // o banco achou a conta mesmo com outra grafia (busca ignora caixa)
        String grafiaDiferente = "marıa@example.com";
        when(usuarioService.buscarPorEmail(grafiaDiferente)).thenReturn(Optional.of(usuario(StatusConta.ATIVA)));
        tentativaLiberada();
        when(senhaHasher.confere(SENHA, "hash-bcrypt")).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(grafiaDiferente, SENHA, DISPOSITIVO, IP))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(tentativaLoginService).registrarSeLiberado(eq(EMAIL), eq(IP), eq(5), any());
    }

    @Test
    void deveTratarContaEncerradaComoEmailInexistente() {
        Usuario encerrado = usuario(StatusConta.ATIVA);
        encerrado.setAnonimizadoEm(Instant.now());
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(encerrado));
        tentativaLiberada();

        assertThatThrownBy(() -> useCase.executar(EMAIL, SENHA, DISPOSITIVO, IP))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(senhaHasher).simularConferencia(SENHA);
        verify(senhaHasher, never()).confere(anyString(), anyString());
    }

    @Test
    void deveRecusarContaSuspensaQuandoASenhaConfereSemContarComoFalha() {
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario(StatusConta.BLOQUEADA)));
        tentativaLiberada();
        when(senhaHasher.confere(SENHA, "hash-bcrypt")).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(EMAIL, SENHA, DISPOSITIVO, IP))
                .isInstanceOf(ContaSuspensaException.class);

        verify(tentativaLoginService).remover(tentativa);
        verifyNoInteractions(tokenService, sessaoService);
    }

    @Test
    void naoDeveRevelarSuspensaoAQuemErraASenha() {
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario(StatusConta.BLOQUEADA)));
        tentativaLiberada();
        when(senhaHasher.confere(SENHA, "hash-bcrypt")).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(EMAIL, SENHA, DISPOSITIVO, IP))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void deveBloquearSemConferirSenhaQuandoOLimiteEstourou() {
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario(StatusConta.ATIVA)));
        when(tentativaLoginService.registrarSeLiberado(anyString(), eq(IP), anyInt(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(EMAIL, SENHA, DISPOSITIVO, IP))
                .isInstanceOf(LoginTemporariamenteBloqueadoException.class);

        verifyNoInteractions(senhaHasher, tokenService, sessaoService);
    }
}
