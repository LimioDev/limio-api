package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.limio.api.modulos.auth.actions.helper.RefreshTokenHelper;
import com.limio.api.modulos.auth.actions.service.TokenService;
import com.limio.api.modulos.auth.actions.usecase.RenovarSessaoUseCase;
import com.limio.api.modulos.auth.excecao.ContaSuspensaException;
import com.limio.api.modulos.auth.excecao.SessaoInvalidaException;
import com.limio.api.modulos.auth.sessao.Sessao;
import com.limio.api.modulos.auth.sessao.SessaoService;
import com.limio.api.modulos.auth.sessao.TokensSessao;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

@ExtendWith(MockitoExtension.class)
class RenovarSessaoUseCaseTest {

    private static final String REFRESH_TOKEN = "refresh-token-do-celular";
    private static final String HASH = RefreshTokenHelper.hash(REFRESH_TOKEN);

    @Mock
    private SessaoService sessaoService;

    @Mock
    private TokenService tokenService;

    private RenovarSessaoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new RenovarSessaoUseCase(sessaoService, tokenService);
    }

    private Sessao sessaoAtiva(StatusConta status) {
        Usuario usuario = Usuario.builder().papelAtivo(PapelUsuario.EMPREGADOR).statusConta(status).build();
        return Sessao.builder()
                .usuario(usuario)
                .refreshTokenHash(HASH)
                .expiraEm(Instant.now().plus(Duration.ofDays(1)))
                .build();
    }

    @Test
    void deveRotacionarSessaoAtivaEDevolverTokensNovos() {
        Sessao sessao = sessaoAtiva(StatusConta.ATIVA);
        when(sessaoService.buscarPorRefreshTokenHashParaRenovar(HASH)).thenReturn(Optional.of(sessao));
        var novos = new TokensSessao("jwt-novo", Instant.now(), "refresh-novo", PapelUsuario.EMPREGADOR);
        when(tokenService.emitir(eq(sessao), any(Instant.class))).thenReturn(novos);

        assertThat(useCase.executar(REFRESH_TOKEN)).isSameAs(novos);

        verify(sessaoService).salvar(sessao);
    }

    @Test
    void deveRecusarSessaoRevogada() {
        Sessao sessao = sessaoAtiva(StatusConta.ATIVA);
        sessao.setRevogadaEm(Instant.now().minusSeconds(60));
        when(sessaoService.buscarPorRefreshTokenHashParaRenovar(HASH)).thenReturn(Optional.of(sessao));

        assertThatThrownBy(() -> useCase.executar(REFRESH_TOKEN)).isInstanceOf(SessaoInvalidaException.class);

        verifyNoInteractions(tokenService);
    }

    @Test
    void deveRecusarSessaoExpirada() {
        Sessao sessao = sessaoAtiva(StatusConta.ATIVA);
        sessao.setExpiraEm(Instant.now().minusSeconds(1));
        when(sessaoService.buscarPorRefreshTokenHashParaRenovar(HASH)).thenReturn(Optional.of(sessao));

        assertThatThrownBy(() -> useCase.executar(REFRESH_TOKEN)).isInstanceOf(SessaoInvalidaException.class);
    }

    @Test
    void deveRecusarRefreshTokenJaRotacionadoOuDesconhecido() {
        when(sessaoService.buscarPorRefreshTokenHashParaRenovar(HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(REFRESH_TOKEN)).isInstanceOf(SessaoInvalidaException.class);
    }

    @Test
    void deveRecusarContaSuspensaDepoisDoLogin() {
        when(sessaoService.buscarPorRefreshTokenHashParaRenovar(HASH))
                .thenReturn(Optional.of(sessaoAtiva(StatusConta.BLOQUEADA)));

        assertThatThrownBy(() -> useCase.executar(REFRESH_TOKEN)).isInstanceOf(ContaSuspensaException.class);

        verifyNoInteractions(tokenService);
    }
}
