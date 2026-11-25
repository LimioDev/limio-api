package com.limio.api.modulos.moderacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.eventos.ContaSuspensaEvent;
import com.limio.api.comum.eventos.EncerramentoDeContaDecididoEvent;
import com.limio.api.comum.eventos.FotoPerfilRemovidaPelaModeracaoEvent;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.moderacao.DenunciaDecidida;
import com.limio.api.modulos.moderacao.PedidoDeDecisao;
import com.limio.api.modulos.moderacao.actions.usecase.DecidirDenunciaUseCase;
import com.limio.api.modulos.moderacao.avaliacao.Avaliacao;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoService;
import com.limio.api.modulos.moderacao.denuncia.AlvoDenuncia;
import com.limio.api.modulos.moderacao.denuncia.DecisaoModeracao;
import com.limio.api.modulos.moderacao.denuncia.DecisaoModeracaoService;
import com.limio.api.modulos.moderacao.denuncia.Denuncia;
import com.limio.api.modulos.moderacao.denuncia.DenunciaService;
import com.limio.api.modulos.moderacao.denuncia.Sancao;
import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;
import com.limio.api.modulos.moderacao.enums.StatusResolucao;
import com.limio.api.modulos.moderacao.enums.TipoSancao;
import com.limio.api.modulos.moderacao.enums.VereditoModeracao;
import com.limio.api.modulos.moderacao.excecao.DecisaoInvalidaException;
import com.limio.api.modulos.moderacao.excecao.DenunciaJaResolvidaException;
import com.limio.api.modulos.moderacao.excecao.DenunciaNaoEncontradaException;

class DecidirDenunciaUseCaseTest {

    private static final UUID DENUNCIA_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID DENUNCIANTE_ID = UUID.randomUUID();
    private static final UUID DENUNCIADO_ID = UUID.randomUUID();

    private final DenunciaService denunciaService = mock(DenunciaService.class);
    private final DecisaoModeracaoService decisaoService = mock(DecisaoModeracaoService.class);
    private final AvaliacaoService avaliacaoService = mock(AvaliacaoService.class);
    private final LogAuditoriaService auditoria = mock(LogAuditoriaService.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);

    private final DecidirDenunciaUseCase useCase = new DecidirDenunciaUseCase(denunciaService, decisaoService,
            avaliacaoService, auditoria, eventos, Clock.systemUTC());

    private Denuncia denuncia;

    @BeforeEach
    void denunciaAberta() {
        denuncia = Denuncia.registrar(DENUNCIANTE_ID, new AlvoDenuncia(DENUNCIADO_ID, null, null, null),
                MotivoDenuncia.SUSPEITA_DE_FRAUDE, "Golpe.", null, Instant.now().plus(Duration.ofHours(72)));
        when(denunciaService.buscarParaDecidir(DENUNCIA_ID)).thenReturn(Optional.of(denuncia));
        when(decisaoService.salvarAgora(any())).thenAnswer(chamada -> chamada.getArgument(0));
        when(decisaoService.salvarSancao(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void improcedenteFechaSemSancaoEAudita() {
        DenunciaDecidida decidida = decidir(pedido(VereditoModeracao.IMPROCEDENTE, null));

        assertThat(decidida.denuncia().getStatusResolucao()).isEqualTo(StatusResolucao.IMPROCEDENTE);
        assertThat(decidida.denuncia().getAnalisadaEm()).isNotNull();
        assertThat(decidida.decisao().getAdministradorId()).isEqualTo(ADMIN_ID);
        assertThat(decidida.sancoes()).isEmpty();
        verify(decisaoService, never()).salvarSancao(any());
        verifyNoInteractions(eventos);
        verify(auditoria).registrar(eq("Denuncia"), eq(DENUNCIA_ID), eq(TipoAcaoAuditoria.DENUNCIA_DECIDIDA), any(),
                eq("10.0.0.1"), anyMap());
    }

    @Test
    void advertenciaGravaSancaoComOFundamento() {
        DenunciaDecidida decidida = decidir(pedido(VereditoModeracao.PROCEDENTE, TipoSancao.ADVERTENCIA));

        assertThat(decidida.denuncia().getStatusResolucao()).isEqualTo(StatusResolucao.PROCEDENTE);
        Sancao sancao = decidida.sancoes().getFirst();
        assertThat(sancao.getTipo()).isEqualTo(TipoSancao.ADVERTENCIA);
        assertThat(sancao.getUsuarioId()).isEqualTo(DENUNCIADO_ID);
        assertThat(sancao.getMotivo()).isEqualTo("Fundamento.");
        verifyNoInteractions(eventos);
    }

    @Test
    void suspensaoPublicaOEventoComOFim() {
        Instant fim = Instant.now().plus(Duration.ofDays(7));

        decidir(new PedidoDeDecisao(VereditoModeracao.PROCEDENTE, "Fundamento.", TipoSancao.SUSPENSAO, Instant.now(),
                fim, null, false));

        ArgumentCaptor<ContaSuspensaEvent> evento = ArgumentCaptor.forClass(ContaSuspensaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().usuarioId()).isEqualTo(DENUNCIADO_ID);
        assertThat(evento.getValue().suspensaAte()).isEqualTo(fim);
    }

    @Test
    void suspensaoComecaNaHoraDaDecisao() {
        Instant agora = Instant.parse("2026-11-25T12:00:00Z");
        DecidirDenunciaUseCase comRelogio = new DecidirDenunciaUseCase(denunciaService, decisaoService,
                avaliacaoService, auditoria, eventos, Clock.fixed(agora, ZoneOffset.UTC));

        DenunciaDecidida decidida = comRelogio.executar(DENUNCIA_ID, ADMIN_ID, new PedidoDeDecisao(
                VereditoModeracao.PROCEDENTE, "Fundamento.", TipoSancao.SUSPENSAO,
                agora.plus(Duration.ofMinutes(2)), agora.plus(Duration.ofDays(7)), null, false), "10.0.0.1");

        assertThat(decidida.sancoes().getFirst().getInicioEm())
                .as("o instante em que a conta bloqueou, não o relógio do navegador").isEqualTo(agora);
    }

    @Test
    void suspensaoComInicioNoFuturoERecusada() {
        Instant inicio = Instant.now().plus(Duration.ofDays(7));

        assertThatThrownBy(() -> decidir(new PedidoDeDecisao(VereditoModeracao.PROCEDENTE, "Fundamento.",
                TipoSancao.SUSPENSAO, inicio, inicio.plus(Duration.ofDays(7)), null, false)))
                .isInstanceOf(DecisaoInvalidaException.class)
                .hasMessageContaining("começa na hora da decisão");
        verifyNoInteractions(eventos);
    }

    @Test
    void encerramentoPublicaOEventoParaOAuth() {
        decidir(pedido(VereditoModeracao.PROCEDENTE, TipoSancao.ENCERRAMENTO_DE_CONTA));

        verify(eventos).publishEvent(any(EncerramentoDeContaDecididoEvent.class));
    }

    @Test
    void removerFotoPublicaOEventoParaOPerfil() {
        decidir(new PedidoDeDecisao(VereditoModeracao.PROCEDENTE, "Fundamento.", TipoSancao.ADVERTENCIA, null, null,
                null, true));

        verify(eventos).publishEvent(any(FotoPerfilRemovidaPelaModeracaoEvent.class));
    }

    @Test
    void ocultarAvaliacaoDeOutraPessoaERecusado() {
        UUID avaliacaoId = UUID.randomUUID();
        Avaliacao deOutro = Avaliacao.publicar(UUID.randomUUID(), UUID.randomUUID(), DENUNCIANTE_ID,
                PapelUsuario.EMPREGADOR, 1, "Ruim", Instant.now());
        when(avaliacaoService.buscarPorId(avaliacaoId)).thenReturn(Optional.of(deOutro));

        assertThatThrownBy(() -> decidir(new PedidoDeDecisao(VereditoModeracao.PROCEDENTE, "Fundamento.",
                TipoSancao.OCULTAR_AVALIACAO, null, null, avaliacaoId, false)))
                .isInstanceOf(DecisaoInvalidaException.class);
        assertThat(denuncia.isAberta()).isTrue();
    }

    @Test
    void recusaPedidosIncoerentes() {
        Instant agora = Instant.now();
        assertInvalido(pedido(VereditoModeracao.IMPROCEDENTE, TipoSancao.ADVERTENCIA));
        assertInvalido(new PedidoDeDecisao(VereditoModeracao.IMPROCEDENTE, "F.", null, null, null, null, true));
        assertInvalido(pedido(VereditoModeracao.PROCEDENTE, null));
        assertInvalido(pedido(VereditoModeracao.PROCEDENTE, TipoSancao.SUSPENSAO));
        assertInvalido(new PedidoDeDecisao(VereditoModeracao.PROCEDENTE, "F.", TipoSancao.SUSPENSAO,
                agora.plus(Duration.ofDays(2)), agora.plus(Duration.ofDays(1)), null, false));
        assertInvalido(new PedidoDeDecisao(VereditoModeracao.PROCEDENTE, "F.", TipoSancao.SUSPENSAO,
                agora.minus(Duration.ofDays(3)), agora.minus(Duration.ofDays(1)), null, false));
        assertInvalido(new PedidoDeDecisao(VereditoModeracao.PROCEDENTE, "F.", TipoSancao.ADVERTENCIA, agora,
                agora.plus(Duration.ofDays(1)), null, false));
        assertInvalido(new PedidoDeDecisao(VereditoModeracao.PROCEDENTE, "F.", TipoSancao.ADVERTENCIA, null, null,
                UUID.randomUUID(), false));
        assertInvalido(pedido(VereditoModeracao.PROCEDENTE, TipoSancao.OCULTAR_AVALIACAO));
        verify(decisaoService, never()).salvarAgora(any(DecisaoModeracao.class));
    }

    @Test
    void denunciaJaDecididaNaoDecideDeNovo() {
        denuncia.resolver(VereditoModeracao.IMPROCEDENTE, Instant.now());

        assertThatThrownBy(() -> decidir(pedido(VereditoModeracao.PROCEDENTE, TipoSancao.ADVERTENCIA)))
                .isInstanceOf(DenunciaJaResolvidaException.class);
    }

    @Test
    void denunciaInexistente() {
        when(denunciaService.buscarParaDecidir(DENUNCIA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> decidir(pedido(VereditoModeracao.IMPROCEDENTE, null)))
                .isInstanceOf(DenunciaNaoEncontradaException.class);
    }

    private void assertInvalido(PedidoDeDecisao pedido) {
        assertThatThrownBy(() -> decidir(pedido)).isInstanceOf(DecisaoInvalidaException.class);
    }

    private DenunciaDecidida decidir(PedidoDeDecisao pedido) {
        return useCase.executar(DENUNCIA_ID, ADMIN_ID, pedido, "10.0.0.1");
    }

    private static PedidoDeDecisao pedido(VereditoModeracao veredito, TipoSancao sancao) {
        return new PedidoDeDecisao(veredito, "Fundamento.", sancao, null, null, null, false);
    }
}
