package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.actions.helper.LinkApp;
import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.actions.helper.TokenRecuperacaoHelper;
import com.limio.api.modulos.auth.actions.usecase.TrocarEmailUseCase;
import com.limio.api.modulos.auth.eventos.ConfirmacaoEmailSolicitadaEvent;
import com.limio.api.modulos.auth.eventos.EmailContaAlteradoEvent;
import com.limio.api.modulos.auth.excecao.ContaSuspensaException;
import com.limio.api.modulos.auth.excecao.ContatoNaoPermitidoException;
import com.limio.api.modulos.auth.excecao.SenhaAtualIncorretaException;
import com.limio.api.modulos.auth.excecao.SessaoInvalidaException;
import com.limio.api.modulos.auth.excecao.TrocaContatoLimiteExcedidaException;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContato;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoService;
import com.limio.api.modulos.auth.verificacaocontato.enums.CanalVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

@ExtendWith(MockitoExtension.class)
class TrocarEmailUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final String FRONTEND_URL = "https://app.limio.test";
    private static final String EMAIL_ATUAL = "maria@example.com";
    private static final String EMAIL_NOVO = "maria.nova@example.com";
    private static final Instant CONFIRMADO = Instant.parse("2026-10-01T10:00:00Z");
    private static final FinalidadeVerificacao FINALIDADE = FinalidadeVerificacao.CONFIRMACAO_EMAIL;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private VerificacaoContatoService verificacaoContatoService;

    @Mock
    private SenhaHasher senhaHasher;

    @Mock
    private ApplicationEventPublisher eventos;

    private TrocarEmailUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new TrocarEmailUseCase(usuarioService, verificacaoContatoService, senhaHasher, eventos,
                new LinkApp(FRONTEND_URL), Duration.ofHours(24), 3, Duration.ofHours(1));
    }

    private <E> E eventoPublicado(Class<E> tipo) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventos, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(tipo::isInstance).map(tipo::cast).findFirst().orElseThrow();
    }

    private Usuario usuario(StatusConta status, Instant emailConfirmadoEm) {
        Usuario usuario = Usuario.builder()
                .email(EMAIL_ATUAL)
                .senhaHash("hash")
                .emailConfirmadoEm(emailConfirmadoEm)
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(status)
                .build();
        ReflectionTestUtils.setField(usuario, "id", USUARIO_ID);
        return usuario;
    }

    private Usuario usuarioAtivo() {
        return usuario(StatusConta.ATIVA, CONFIRMADO);
    }

    private void autenticarComSenhaCerta(Usuario usuario) {
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID)).thenReturn(usuario);
        when(senhaHasher.confere("senha", "hash")).thenReturn(true);
    }

    private void limiteLivre() {
        when(verificacaoContatoService.contarDesde(eq(USUARIO_ID), eq(FINALIDADE), any())).thenReturn(0L);
    }

    /** Todos os tokens passados a {@code salvar} (os consumidos e o novo), na ordem das chamadas. */
    private List<VerificacaoContato> tokensSalvos() {
        ArgumentCaptor<VerificacaoContato> captor = ArgumentCaptor.forClass(VerificacaoContato.class);
        verify(verificacaoContatoService, atLeastOnce()).salvar(captor.capture());
        return captor.getAllValues();
    }

    /** O token novo é sempre o último salvo (a invalidação dos antigos vem antes). */
    private VerificacaoContato tokenSalvo() {
        List<VerificacaoContato> salvos = tokensSalvos();
        return salvos.get(salvos.size() - 1);
    }

    @Test
    void deveTrocarOEmailLimparAConfirmacaoEEmitirUmLink() {
        Usuario usuario = usuarioAtivo();
        autenticarComSenhaCerta(usuario);
        when(usuarioService.buscarPorEmail(EMAIL_NOVO)).thenReturn(Optional.empty());
        limiteLivre();
        when(verificacaoContatoService.pendentesDoUsuario(USUARIO_ID, FINALIDADE)).thenReturn(List.of());

        Usuario atualizado = useCase.executar(USUARIO_ID, EMAIL_NOVO, "senha");

        assertThat(atualizado.getEmail()).isEqualTo(EMAIL_NOVO);
        assertThat(atualizado.getEmailConfirmadoEm()).isNull();
        VerificacaoContato token = tokenSalvo();
        assertThat(token.getCanal()).isEqualTo(CanalVerificacao.EMAIL);
        assertThat(token.getFinalidade()).isEqualTo(FINALIDADE);
        assertThat(token.getTokenHash()).hasSize(64);
        assertThat(token.getExpiraEm()).isAfter(Instant.now().plus(Duration.ofHours(23)));
        assertThat(token.getUsuario()).isSameAs(usuario);
    }

    @Test
    void deveCarregarComLockContarLimiteGravarEDepoisMexerNosTokens() {
        Usuario usuario = usuarioAtivo();
        autenticarComSenhaCerta(usuario);
        when(usuarioService.buscarPorEmail(EMAIL_NOVO)).thenReturn(Optional.empty());
        limiteLivre();
        when(verificacaoContatoService.pendentesDoUsuario(USUARIO_ID, FINALIDADE)).thenReturn(List.of());

        useCase.executar(USUARIO_ID, EMAIL_NOVO, "senha");

        InOrder ordem = inOrder(usuarioService, verificacaoContatoService);
        ordem.verify(usuarioService).buscarParaAtualizarOuFalhar(USUARIO_ID);
        ordem.verify(verificacaoContatoService).contarDesde(eq(USUARIO_ID), eq(FINALIDADE), any());
        ordem.verify(usuarioService).salvarComEmailUnico(usuario);
        ordem.verify(verificacaoContatoService).pendentesDoUsuario(USUARIO_ID, FINALIDADE);
        ordem.verify(verificacaoContatoService).salvar(any(VerificacaoContato.class));
    }

    @Test
    void devePublicarOEventoComOLinkDoTokenGravado() {
        autenticarComSenhaCerta(usuarioAtivo());
        when(usuarioService.buscarPorEmail(EMAIL_NOVO)).thenReturn(Optional.empty());
        limiteLivre();
        when(verificacaoContatoService.pendentesDoUsuario(USUARIO_ID, FINALIDADE)).thenReturn(List.of());

        useCase.executar(USUARIO_ID, EMAIL_NOVO, "senha");

        ConfirmacaoEmailSolicitadaEvent evento = eventoPublicado(ConfirmacaoEmailSolicitadaEvent.class);
        assertThat(evento.destinatario()).isEqualTo(EMAIL_NOVO);
        assertThat(evento.link()).startsWith(FRONTEND_URL + "/confirmar-email?token=");
        String token = evento.link().substring(evento.link().indexOf("token=") + "token=".length());
        assertThat(TokenRecuperacaoHelper.hash(token)).isEqualTo(tokenSalvo().getTokenHash());
    }

    @Test
    void deveInvalidarOsTokensPendentesAnteriores() {
        autenticarComSenhaCerta(usuarioAtivo());
        when(usuarioService.buscarPorEmail(EMAIL_NOVO)).thenReturn(Optional.empty());
        limiteLivre();
        VerificacaoContato antigo = VerificacaoContato.builder().finalidade(FINALIDADE).build();
        VerificacaoContato outroAntigo = VerificacaoContato.builder().finalidade(FINALIDADE).build();
        when(verificacaoContatoService.pendentesDoUsuario(USUARIO_ID, FINALIDADE))
                .thenReturn(List.of(antigo, outroAntigo));

        useCase.executar(USUARIO_ID, EMAIL_NOVO, "senha");

        assertThat(antigo.getConsumidoEm()).isNotNull();
        assertThat(outroAntigo.getConsumidoEm()).isNotNull();
        List<VerificacaoContato> salvos = tokensSalvos();
        assertThat(salvos).hasSize(3);
        assertThat(salvos.get(0)).isSameAs(antigo);
        assertThat(salvos.get(1)).isSameAs(outroAntigo);
        assertThat(salvos.get(2).getConsumidoEm()).as("token novo segue pendente").isNull();
    }

    @Test
    void emailDeOutraContaDeveSerRecusadoSemGravarNemEnviar() {
        Usuario usuario = usuarioAtivo();
        autenticarComSenhaCerta(usuario);
        Usuario outraConta = usuario(StatusConta.ATIVA, null);
        ReflectionTestUtils.setField(outraConta, "id", UUID.randomUUID());
        when(usuarioService.buscarPorEmail(EMAIL_NOVO)).thenReturn(Optional.of(outraConta));

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, EMAIL_NOVO, "senha"))
                .isInstanceOf(ContatoNaoPermitidoException.class);

        assertThat(usuario.getEmail()).isEqualTo(EMAIL_ATUAL);
        verify(usuarioService, never()).salvarComEmailUnico(any());
        verifyNoInteractions(verificacaoContatoService, eventos);
    }

    @Test
    void violacaoDeUnicidadeNaGravacaoNaoDeveInvalidarNemCriarTokenNemEnviar() {
        Usuario usuario = usuarioAtivo();
        autenticarComSenhaCerta(usuario);
        when(usuarioService.buscarPorEmail(EMAIL_NOVO)).thenReturn(Optional.empty());
        limiteLivre();
        doThrow(new ContatoNaoPermitidoException()).when(usuarioService).salvarComEmailUnico(usuario);

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, EMAIL_NOVO, "senha"))
                .isInstanceOf(ContatoNaoPermitidoException.class);

        verify(verificacaoContatoService, never()).pendentesDoUsuario(any(), any());
        verify(verificacaoContatoService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void senhaErradaNaoDeveAlterarNada() {
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID)).thenReturn(usuarioAtivo());
        when(senhaHasher.confere("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, EMAIL_NOVO, "errada"))
                .isInstanceOf(SenhaAtualIncorretaException.class);

        verifyNoMoreInteractions(usuarioService);
        verifyNoInteractions(verificacaoContatoService, eventos);
    }

    @Test
    void contaSuspensaEEncerradaNaoPodemTrocar() {
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID))
                .thenReturn(usuario(StatusConta.BLOQUEADA, CONFIRMADO));
        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, EMAIL_NOVO, "senha"))
                .isInstanceOf(ContaSuspensaException.class);

        Usuario encerrada = usuarioAtivo();
        encerrada.setAnonimizadoEm(CONFIRMADO);
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID)).thenReturn(encerrada);
        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, EMAIL_NOVO, "senha"))
                .isInstanceOf(SessaoInvalidaException.class);

        verifyNoInteractions(verificacaoContatoService, eventos, senhaHasher);
    }

    @Test
    void limiteExcedidoNaoDeveGravarNemEmitir() {
        Usuario usuario = usuarioAtivo();
        autenticarComSenhaCerta(usuario);
        when(usuarioService.buscarPorEmail(EMAIL_NOVO)).thenReturn(Optional.empty());
        when(verificacaoContatoService.contarDesde(eq(USUARIO_ID), eq(FINALIDADE), any())).thenReturn(3L);

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, EMAIL_NOVO, "senha"))
                .isInstanceOf(TrocaContatoLimiteExcedidaException.class);

        assertThat(usuario.getEmail()).isEqualTo(EMAIL_ATUAL);
        verify(usuarioService, never()).salvarComEmailUnico(any());
        verify(verificacaoContatoService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void devePublicarOAvisoParaOEmailAntigoDepoisDoLink() {
        autenticarComSenhaCerta(usuarioAtivo());
        when(usuarioService.buscarPorEmail(EMAIL_NOVO)).thenReturn(Optional.empty());
        limiteLivre();
        when(verificacaoContatoService.pendentesDoUsuario(USUARIO_ID, FINALIDADE)).thenReturn(List.of());

        useCase.executar(USUARIO_ID, EMAIL_NOVO, "senha");

        InOrder ordem = inOrder(eventos);
        ordem.verify(eventos).publishEvent(any(ConfirmacaoEmailSolicitadaEvent.class));
        ordem.verify(eventos).publishEvent(new EmailContaAlteradoEvent(EMAIL_ATUAL, EMAIL_NOVO));
    }

    /** D8: mesmo e-mail ainda não confirmado reemite o link. */
    @Test
    void mesmoEmailNaoConfirmadoDeveReemitirOLinkSemAlterarOEmail() {
        Usuario usuario = usuario(StatusConta.ATIVA, null);
        autenticarComSenhaCerta(usuario);
        limiteLivre();
        VerificacaoContato antigo = VerificacaoContato.builder().finalidade(FINALIDADE).build();
        when(verificacaoContatoService.pendentesDoUsuario(USUARIO_ID, FINALIDADE)).thenReturn(List.of(antigo));

        useCase.executar(USUARIO_ID, EMAIL_ATUAL, "senha");

        assertThat(usuario.getEmail()).isEqualTo(EMAIL_ATUAL);
        verify(usuarioService, never()).salvarComEmailUnico(any());
        verify(usuarioService, never()).buscarPorEmail(anyString());
        assertThat(antigo.getConsumidoEm()).isNotNull();
        assertThat(tokenSalvo().getFinalidade()).isEqualTo(FINALIDADE);
        assertThat(eventoPublicado(ConfirmacaoEmailSolicitadaEvent.class).destinatario()).isEqualTo(EMAIL_ATUAL);
        verify(eventos, never()).publishEvent(any(EmailContaAlteradoEvent.class));
    }

    @Test
    void reemissaoDeveContarNoLimite() {
        autenticarComSenhaCerta(usuario(StatusConta.ATIVA, null));
        when(verificacaoContatoService.contarDesde(eq(USUARIO_ID), eq(FINALIDADE), any())).thenReturn(3L);

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, EMAIL_ATUAL, "senha"))
                .isInstanceOf(TrocaContatoLimiteExcedidaException.class);

        verify(verificacaoContatoService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    /** D8: mesmo e-mail já confirmado é no-op. */
    @Test
    void mesmoEmailConfirmadoNaoDeveFazerNada() {
        Usuario usuario = usuarioAtivo();
        autenticarComSenhaCerta(usuario);

        useCase.executar(USUARIO_ID, EMAIL_ATUAL, "senha");

        assertThat(usuario.getEmailConfirmadoEm()).isEqualTo(CONFIRMADO);
        verify(usuarioService, never()).salvarComEmailUnico(any());
        verifyNoInteractions(verificacaoContatoService, eventos);
    }

    @Test
    void mesmoEmailComOutraCaixaDeveSerTratadoComoIgual() {
        Usuario usuario = usuarioAtivo();
        autenticarComSenhaCerta(usuario);

        useCase.executar(USUARIO_ID, "MARIA@Example.com", "senha");

        verify(usuarioService, never()).salvarComEmailUnico(any());
        verifyNoInteractions(verificacaoContatoService, eventos);
    }
}
