package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.limio.api.comum.eventos.ContaSuspensaEvent;
import com.limio.api.modulos.auth.actions.usecase.SuspenderContaUseCase;
import com.limio.api.modulos.auth.sessao.SessaoService;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

/** Ciclo de vida da suspensão do lado do {@code auth} (ETI-116, review da PR #53). */
class SuspenderContaUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-25T12:00:00Z");

    private final UsuarioService usuarioService = mock(UsuarioService.class);
    private final SessaoService sessaoService = mock(SessaoService.class);
    private final TransactionTemplate transacao = mock(TransactionTemplate.class);
    private final SuspenderContaUseCase useCase = new SuspenderContaUseCase(usuarioService, sessaoService, transacao,
            Clock.fixed(AGORA, ZoneOffset.UTC));

    @BeforeEach
    void transacaoExecutaNaHora() {
        when(transacao.execute(any())).thenAnswer(chamada -> {
            TransactionCallback<?> callback = chamada.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void contaAtivaFicaBloqueadaAteOFimEPerdeAsSessoes() {
        Usuario usuario = usuario(StatusConta.ATIVA, null);
        Instant fim = AGORA.plus(Duration.ofDays(7));

        suspender(usuario, fim);

        assertThat(usuario.getStatusConta()).isEqualTo(StatusConta.BLOQUEADA);
        assertThat(usuario.getSuspensaAte()).isEqualTo(fim);
        verify(sessaoService).revogarTodas(usuario.getId(), null, AGORA);
    }

    @Test
    void bloqueioSemPrazoContinuaSemPrazo() {
        Usuario usuario = usuario(StatusConta.BLOQUEADA, null);

        suspender(usuario, AGORA.plus(Duration.ofDays(7)));

        assertThat(usuario.getStatusConta()).isEqualTo(StatusConta.BLOQUEADA);
        assertThat(usuario.getSuspensaAte()).as("a rotina não mexe em bloqueio sem prazo").isNull();
    }

    @Test
    void suspensaoMaisCurtaNaoEncurtaAQueJaVale() {
        Instant atual = AGORA.plus(Duration.ofDays(60));
        Usuario usuario = usuario(StatusConta.BLOQUEADA, atual);

        suspender(usuario, AGORA.plus(Duration.ofDays(15)));

        assertThat(usuario.getSuspensaAte()).isEqualTo(atual);
    }

    @Test
    void suspensaoMaisLongaEstendeOPrazo() {
        Usuario usuario = usuario(StatusConta.BLOQUEADA, AGORA.plus(Duration.ofDays(2)));
        Instant fim = AGORA.plus(Duration.ofDays(30));

        suspender(usuario, fim);

        assertThat(usuario.getSuspensaAte()).isEqualTo(fim);
    }

    @Test
    void rotinaReativaASuspensaoVencida() {
        Usuario vencida = usuario(StatusConta.BLOQUEADA, AGORA.minus(Duration.ofMinutes(1)));
        rotinaEncontra(vencida);

        useCase.encerrarSuspensoesVencidas();

        assertThat(vencida.getStatusConta()).isEqualTo(StatusConta.ATIVA);
        assertThat(vencida.getSuspensaAte()).isNull();
        verify(usuarioService).salvar(vencida);
    }

    @Test
    void rotinaNaoDesfazSuspensaoEstendidaDepoisDaBusca() {
        // a busca dos ids viu o prazo vencido, mas ao travar a linha uma suspensão nova já tinha estendido o prazo
        Usuario estendida = usuario(StatusConta.BLOQUEADA, AGORA.plus(Duration.ofDays(10)));
        rotinaEncontra(estendida);

        useCase.encerrarSuspensoesVencidas();

        assertThat(estendida.getStatusConta()).isEqualTo(StatusConta.BLOQUEADA);
        verify(usuarioService, never()).salvar(any());
    }

    @Test
    void rotinaNaoReativaContaEncerrada() {
        Usuario encerrada = usuario(StatusConta.BLOQUEADA, AGORA.minus(Duration.ofMinutes(1)));
        ReflectionTestUtils.setField(encerrada, "anonimizadoEm", AGORA.minus(Duration.ofDays(1)));
        rotinaEncontra(encerrada);

        useCase.encerrarSuspensoesVencidas();

        assertThat(encerrada.getStatusConta()).isEqualTo(StatusConta.BLOQUEADA);
        verify(usuarioService, never()).salvar(any());
    }

    @Test
    void encerrarContaSuspensaLimpaOPrazo() {
        Usuario usuario = usuario(StatusConta.BLOQUEADA, AGORA.plus(Duration.ofDays(7)));

        usuario.encerrar(AGORA);

        assertThat(usuario.getSuspensaAte()).isNull();
        assertThat(usuario.suspensaoVenceuEm(AGORA.plus(Duration.ofDays(30)))).isFalse();
    }

    @Test
    void falhaNumaContaNaoImpedeAsOutras() {
        Usuario quebrada = usuario(StatusConta.BLOQUEADA, AGORA.minus(Duration.ofMinutes(1)));
        Usuario vencida = usuario(StatusConta.BLOQUEADA, AGORA.minus(Duration.ofMinutes(1)));
        when(usuarioService.buscarIdsComSuspensaoVencida(AGORA)).thenReturn(List.of(quebrada.getId(), vencida.getId()));
        when(usuarioService.buscarParaAtualizarOuFalhar(quebrada.getId())).thenThrow(new IllegalStateException("x"));
        when(usuarioService.buscarParaAtualizarOuFalhar(vencida.getId())).thenReturn(vencida);

        useCase.encerrarSuspensoesVencidas();

        assertThat(vencida.getStatusConta()).isEqualTo(StatusConta.ATIVA);
    }

    private void suspender(Usuario usuario, Instant fim) {
        when(usuarioService.buscarParaAtualizarOuFalhar(usuario.getId())).thenReturn(usuario);
        useCase.aoSuspenderConta(new ContaSuspensaEvent(usuario.getId(), fim, AGORA));
    }

    private void rotinaEncontra(Usuario usuario) {
        when(usuarioService.buscarIdsComSuspensaoVencida(AGORA)).thenReturn(List.of(usuario.getId()));
        when(usuarioService.buscarParaAtualizarOuFalhar(usuario.getId())).thenReturn(usuario);
    }

    private static Usuario usuario(StatusConta status, Instant suspensaAte) {
        Usuario usuario = Usuario.builder().nomeCompleto("Carlos Souza").statusConta(status).suspensaAte(suspensaAte)
                .build();
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        return usuario;
    }
}
