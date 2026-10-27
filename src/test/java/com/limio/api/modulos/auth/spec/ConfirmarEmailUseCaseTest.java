package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.actions.helper.TokenVerificacaoHelper;
import com.limio.api.modulos.auth.actions.usecase.ConfirmarEmailUseCase;
import com.limio.api.modulos.auth.excecao.LinkConfirmacaoEmailInvalidoException;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.auth.verificacaocontato.ConsumoTokenVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContato;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoService;
import com.limio.api.modulos.auth.verificacaocontato.enums.CanalVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

@ExtendWith(MockitoExtension.class)
class ConfirmarEmailUseCaseTest {

    private static final String TOKEN = "token-de-confirmacao";
    private static final String HASH = TokenVerificacaoHelper.hash(TOKEN);
    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final FinalidadeVerificacao FINALIDADE = FinalidadeVerificacao.CONFIRMACAO_EMAIL;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private VerificacaoContatoService verificacaoContatoService;

    private ConfirmarEmailUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        ConsumoTokenVerificacao consumoTokenVerificacao = new ConsumoTokenVerificacao(usuarioService,
                verificacaoContatoService);
        useCase = new ConfirmarEmailUseCase(usuarioService, verificacaoContatoService, consumoTokenVerificacao);
    }

    private Usuario usuario() {
        return Usuario.builder()
                .email("maria@example.com")
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(StatusConta.ATIVA)
                .build();
    }

    private VerificacaoContato token(Usuario usuario, Instant expiraEm) {
        return VerificacaoContato.builder()
                .usuario(usuario)
                .canal(CanalVerificacao.EMAIL)
                .finalidade(FINALIDADE)
                .tokenHash(HASH)
                .expiraEm(expiraEm)
                .build();
    }

    private VerificacaoContato tokenValido(Usuario usuario) {
        return token(usuario, Instant.now().plus(Duration.ofHours(24)));
    }

    private void tokenEncontrado(Usuario usuario, VerificacaoContato verificacao) {
        when(verificacaoContatoService.buscarUsuarioIdPorTokenHash(HASH, FINALIDADE)).thenReturn(Optional.of(USUARIO_ID));
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID)).thenReturn(usuario);
        when(verificacaoContatoService.buscarParaConsumir(HASH, FINALIDADE)).thenReturn(Optional.of(verificacao));
    }

    @Test
    void deveConfirmarOEmailEConsumirOToken() {
        Usuario usuario = usuario();
        VerificacaoContato verificacao = tokenValido(usuario);
        tokenEncontrado(usuario, verificacao);

        useCase.executar(TOKEN);

        assertThat(usuario.getEmailConfirmadoEm()).isNotNull();
        assertThat(verificacao.getConsumidoEm()).isNotNull();
        verify(usuarioService).salvar(usuario);
        verify(verificacaoContatoService).salvar(verificacao);
    }

    @Test
    void deveTravarAContaAntesDeRelerOToken() {
        Usuario usuario = usuario();
        tokenEncontrado(usuario, tokenValido(usuario));

        useCase.executar(TOKEN);

        InOrder ordem = inOrder(verificacaoContatoService, usuarioService);
        ordem.verify(verificacaoContatoService).buscarUsuarioIdPorTokenHash(HASH, FINALIDADE);
        ordem.verify(usuarioService).buscarParaAtualizarOuFalhar(USUARIO_ID);
        ordem.verify(verificacaoContatoService).buscarParaConsumir(HASH, FINALIDADE);
    }

    @Test
    void tokenInexistenteDeveSerRecusadoSemTravarNada() {
        when(verificacaoContatoService.buscarUsuarioIdPorTokenHash(HASH, FINALIDADE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(TOKEN)).isInstanceOf(LinkConfirmacaoEmailInvalidoException.class);

        verifyNoInteractions(usuarioService);
    }

    @Test
    void tokenExpiradoDeveSerRecusado() {
        Usuario usuario = usuario();
        tokenEncontrado(usuario, token(usuario, Instant.now().minusSeconds(1)));

        assertThatThrownBy(() -> useCase.executar(TOKEN)).isInstanceOf(LinkConfirmacaoEmailInvalidoException.class);

        assertThat(usuario.getEmailConfirmadoEm()).isNull();
        verify(usuarioService, never()).salvar(any());
    }

    @Test
    void tokenJaUsadoDeveSerRecusado() {
        Usuario usuario = usuario();
        VerificacaoContato usado = tokenValido(usuario);
        usado.consumir(Instant.now());
        tokenEncontrado(usuario, usado);

        assertThatThrownBy(() -> useCase.executar(TOKEN)).isInstanceOf(LinkConfirmacaoEmailInvalidoException.class);

        verify(usuarioService, never()).salvar(any());
    }

    @Test
    void tokenEmitidoParaOutroEmailDeveSerRecusado() {
        Usuario usuario = usuario();
        VerificacaoContato paraOutro = tokenValido(usuario);
        paraOutro.setDestinoHash(TokenVerificacaoHelper.hashDestino("antigo@example.com"));
        tokenEncontrado(usuario, paraOutro);

        assertThatThrownBy(() -> useCase.executar(TOKEN)).isInstanceOf(LinkConfirmacaoEmailInvalidoException.class);

        assertThat(usuario.getEmailConfirmadoEm()).isNull();
    }

    @Test
    void tokenDoMesmoEmailComOutraCaixaDeveValer() {
        Usuario usuario = usuario();
        VerificacaoContato verificacao = tokenValido(usuario);
        verificacao.setDestinoHash(TokenVerificacaoHelper.hashDestino("Maria@Example.com"));
        tokenEncontrado(usuario, verificacao);

        useCase.executar(TOKEN);

        assertThat(usuario.getEmailConfirmadoEm()).isNotNull();
    }

    @Test
    void contaEncerradaDeveSerRecusada() {
        Usuario encerrada = usuario();
        encerrada.setAnonimizadoEm(Instant.now());
        VerificacaoContato verificacao = tokenValido(encerrada);
        tokenEncontrado(encerrada, verificacao);

        assertThatThrownBy(() -> useCase.executar(TOKEN)).isInstanceOf(LinkConfirmacaoEmailInvalidoException.class);

        assertThat(verificacao.getConsumidoEm()).isNull();
        verify(usuarioService, never()).salvar(any());
    }
}
