package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.limio.api.comum.eventos.ContaEncerradaEvent;
import com.limio.api.modulos.notificacao.MinhasNotificacoes;
import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.NotificacaoService;
import com.limio.api.modulos.notificacao.actions.usecase.ListarNotificacoesUseCase;
import com.limio.api.modulos.notificacao.actions.usecase.MarcarNotificacaoComoLidaUseCase;
import com.limio.api.modulos.notificacao.actions.usecase.MarcarTodasNotificacoesComoLidasUseCase;
import com.limio.api.modulos.notificacao.actions.usecase.RemoverNotificacoesDaContaEncerradaUseCase;
import com.limio.api.modulos.notificacao.enums.TipoNotificacao;
import com.limio.api.modulos.notificacao.excecao.NotificacaoNaoEncontradaException;

@ExtendWith(MockitoExtension.class)
class NotificacaoUseCasesTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID NOTIFICACAO_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-16T15:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Mock
    private NotificacaoService notificacaoService;

    private static Notificacao notificacao(Instant lidaEm) {
        return Notificacao.builder()
                .usuarioId(USUARIO_ID)
                .tipo(TipoNotificacao.NOVA_CANDIDATURA)
                .titulo("Novo candidato")
                .corpo("Rafael S. se candidatou a 'Troca de chuveiro'")
                .enviadaEm(AGORA.minusSeconds(3600))
                .lidaEm(lidaEm)
                .build();
    }

    // ---------- entidade ----------

    @Test
    void dadoNaoLida_quandoMarca_entaoGuardaAData() {
        Notificacao naoLida = notificacao(null);

        naoLida.marcarComoLida(AGORA);

        assertThat(naoLida.getLidaEm()).isEqualTo(AGORA);
        assertThat(naoLida.isLida()).isTrue();
    }

    @Test
    void dadoJaLida_quandoMarcaDeNovo_entaoMantemADataDaPrimeiraLeitura() {
        Instant primeira = AGORA.minusSeconds(60);
        Notificacao lida = notificacao(primeira);

        lida.marcarComoLida(AGORA);

        assertThat(lida.getLidaEm()).isEqualTo(primeira);
    }

    // ---------- listar ----------

    @Test
    void dadoUsuario_quandoLista_entaoDevolveAPaginaPedidaDeVinteEOTotalDeNaoLidas() {
        var pagina = new PageImpl<>(List.of(notificacao(null)));
        when(notificacaoService.buscarDoUsuario(any(UUID.class), any(Pageable.class))).thenReturn(pagina);
        when(notificacaoService.contarNaoLidas(USUARIO_ID)).thenReturn(7L);

        MinhasNotificacoes minhas = new ListarNotificacoesUseCase(notificacaoService).executar(USUARIO_ID, 2);

        assertThat(minhas.pagina()).isSameAs(pagina);
        assertThat(minhas.naoLidas()).isEqualTo(7);
        verify(notificacaoService).buscarDoUsuario(USUARIO_ID, PageRequest.of(2, 20));
    }

    // ---------- marcar uma ----------

    @Test
    void dadoNotificacaoDoUsuario_quandoMarcaComoLida_entaoSalvaComOAgoraEDevolveNaoLidas() {
        Notificacao naoLida = notificacao(null);
        when(notificacaoService.buscarDoUsuario(NOTIFICACAO_ID, USUARIO_ID)).thenReturn(Optional.of(naoLida));
        when(notificacaoService.contarNaoLidas(USUARIO_ID)).thenReturn(2L);

        long naoLidas = new MarcarNotificacaoComoLidaUseCase(notificacaoService, RELOGIO)
                .executar(NOTIFICACAO_ID, USUARIO_ID);

        assertThat(naoLidas).isEqualTo(2);
        ArgumentCaptor<Notificacao> salva = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoService).salvar(salva.capture());
        assertThat(salva.getValue().getLidaEm()).isEqualTo(AGORA);
    }

    /** O service já filtra por dono: notificação de outra pessoa chega aqui como vazio, igual a id inexistente. */
    @Test
    void dadoNotificacaoDeOutroUsuarioOuInexistente_quandoMarca_entao404SemSalvar() {
        when(notificacaoService.buscarDoUsuario(NOTIFICACAO_ID, USUARIO_ID)).thenReturn(Optional.empty());
        var useCase = new MarcarNotificacaoComoLidaUseCase(notificacaoService, RELOGIO);

        assertThatThrownBy(() -> useCase.executar(NOTIFICACAO_ID, USUARIO_ID))
                .isInstanceOf(NotificacaoNaoEncontradaException.class);

        verify(notificacaoService, never()).salvar(any());
    }

    // ---------- marcar todas ----------

    @Test
    void dadoUsuario_quandoMarcaTodas_entaoUmUpdateComOAgoraEDevolveNaoLidas() {
        when(notificacaoService.contarNaoLidas(USUARIO_ID)).thenReturn(0L);

        long naoLidas = new MarcarTodasNotificacoesComoLidasUseCase(notificacaoService, RELOGIO).executar(USUARIO_ID);

        assertThat(naoLidas).isZero();
        verify(notificacaoService).marcarTodasComoLidas(USUARIO_ID, AGORA);
    }

    // ---------- conta encerrada ----------

    @Test
    void dadoContaEncerrada_quandoEventoChega_entaoApagaAsNotificacoesDoUsuario() {
        new RemoverNotificacoesDaContaEncerradaUseCase(notificacaoService)
                .aoEncerrarConta(new ContaEncerradaEvent(USUARIO_ID, AGORA));

        verify(notificacaoService).apagarDoUsuario(USUARIO_ID);
    }
}
