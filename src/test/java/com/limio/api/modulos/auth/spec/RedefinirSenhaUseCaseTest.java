package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.limio.api.modulos.auth.actions.helper.SenhaHasher;
import com.limio.api.modulos.auth.actions.helper.TokenVerificacaoHelper;
import com.limio.api.modulos.auth.actions.usecase.RedefinirSenhaUseCase;
import com.limio.api.modulos.auth.excecao.SenhaIgualAnteriorException;
import com.limio.api.modulos.auth.excecao.TokenRecuperacaoInvalidoException;
import com.limio.api.modulos.auth.sessao.SessaoService;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.auth.verificacaocontato.ConsumoTokenVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContato;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoService;
import com.limio.api.modulos.auth.verificacaocontato.enums.CanalVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

@ExtendWith(MockitoExtension.class)
class RedefinirSenhaUseCaseTest {

    private static final String TOKEN = "token-de-recuperacao";
    private static final String HASH = TokenVerificacaoHelper.hash(TOKEN);
    private static final String NOVA_SENHA = "senhaNova123";

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private VerificacaoContatoService verificacaoContatoService;

    @Mock
    private SessaoService sessaoService;

    @Mock
    private SenhaHasher senhaHasher;

    private RedefinirSenhaUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        ConsumoTokenVerificacao consumoTokenVerificacao = new ConsumoTokenVerificacao(usuarioService,
                verificacaoContatoService);
        useCase = new RedefinirSenhaUseCase(usuarioService, verificacaoContatoService, consumoTokenVerificacao,
                sessaoService, senhaHasher);
    }

    private VerificacaoContato verificacaoValida(Usuario usuario) {
        return VerificacaoContato.builder()
                .usuario(usuario)
                .canal(CanalVerificacao.EMAIL)
                .finalidade(FinalidadeVerificacao.RECUPERACAO_SENHA)
                .tokenHash(HASH)
                .expiraEm(Instant.now().plus(Duration.ofHours(1)))
                .build();
    }

    private static final UUID USUARIO_ID = UUID.randomUUID();

    /** Simula o caminho do consumo: dono do token, conta travada, token relido travado. */
    private void tokenEncontrado(Usuario usuario, VerificacaoContato verificacao) {
        when(verificacaoContatoService.buscarUsuarioIdPorTokenHash(HASH, FinalidadeVerificacao.RECUPERACAO_SENHA))
                .thenReturn(Optional.of(USUARIO_ID));
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID)).thenReturn(usuario);
        when(verificacaoContatoService.buscarParaConsumir(HASH, FinalidadeVerificacao.RECUPERACAO_SENHA))
                .thenReturn(Optional.of(verificacao));
    }

    private Usuario usuario() {
        return Usuario.builder()
                .email("maria@example.com")
                .senhaHash("hash-antigo")
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(StatusConta.ATIVA)
                .build();
    }

    @Test
    void deveRedefinirSenhaConsumirTokenEEncerrarTodasAsSessoes() {
        Usuario usuario = usuario();
        VerificacaoContato verificacao = verificacaoValida(usuario);
        tokenEncontrado(verificacao.getUsuario(), verificacao);
        when(senhaHasher.confere(NOVA_SENHA, "hash-antigo")).thenReturn(false);
        when(senhaHasher.hash(NOVA_SENHA)).thenReturn("hash-novo");

        useCase.executar(TOKEN, NOVA_SENHA);

        assertThat(usuario.getSenhaHash()).isEqualTo("hash-novo");
        assertThat(verificacao.getConsumidoEm()).isNotNull();
        verify(verificacaoContatoService).salvar(verificacao);
        verify(sessaoService).revogarTodas(eq(usuario.getId()), isNull(), any(Instant.class));
    }

    @Test
    void deveTravarAContaAntesDeLerOToken() {
        Usuario usuario = usuario();
        VerificacaoContato verificacao = verificacaoValida(usuario);
        tokenEncontrado(usuario, verificacao);
        when(senhaHasher.confere(NOVA_SENHA, "hash-antigo")).thenReturn(false);
        when(senhaHasher.hash(NOVA_SENHA)).thenReturn("hash-novo");

        useCase.executar(TOKEN, NOVA_SENHA);

        InOrder ordem = inOrder(usuarioService, verificacaoContatoService);
        ordem.verify(usuarioService).buscarParaAtualizarOuFalhar(USUARIO_ID);
        ordem.verify(verificacaoContatoService).buscarParaConsumir(HASH, FinalidadeVerificacao.RECUPERACAO_SENHA);
    }

    @Test
    void deveRecusarLinkEnviadoParaUmEmailQueAContaNaoTemMais() {
        Usuario usuario = usuario();
        VerificacaoContato verificacao = verificacaoValida(usuario);
        verificacao.setDestinoHash(TokenVerificacaoHelper.hashDestino("antigo@example.com"));
        tokenEncontrado(usuario, verificacao);

        assertThatThrownBy(() -> useCase.executar(TOKEN, NOVA_SENHA))
                .isInstanceOf(TokenRecuperacaoInvalidoException.class);

        verify(usuarioService, never()).salvar(any());
    }

    @Test
    void deveRecusarTokenDeContaEncerrada() {
        Usuario encerrada = usuario();
        encerrada.setAnonimizadoEm(Instant.now());
        tokenEncontrado(encerrada, verificacaoValida(encerrada));

        assertThatThrownBy(() -> useCase.executar(TOKEN, NOVA_SENHA))
                .isInstanceOf(TokenRecuperacaoInvalidoException.class);

        verify(usuarioService, never()).salvar(any());
    }

    @Test
    void deveRecusarTokenInexistente() {
        when(verificacaoContatoService.buscarUsuarioIdPorTokenHash(HASH, FinalidadeVerificacao.RECUPERACAO_SENHA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(TOKEN, NOVA_SENHA))
                .isInstanceOf(TokenRecuperacaoInvalidoException.class);

        verify(sessaoService, never()).revogarTodas(any(), any(), any());
    }

    @Test
    void deveRecusarTokenJaConsumido() {
        VerificacaoContato consumido = verificacaoValida(usuario());
        consumido.consumir(Instant.now());
        tokenEncontrado(consumido.getUsuario(), consumido);

        assertThatThrownBy(() -> useCase.executar(TOKEN, NOVA_SENHA))
                .isInstanceOf(TokenRecuperacaoInvalidoException.class);
    }

    @Test
    void deveRecusarTokenExpirado() {
        VerificacaoContato expirado = VerificacaoContato.builder()
                .usuario(usuario())
                .canal(CanalVerificacao.EMAIL)
                .finalidade(FinalidadeVerificacao.RECUPERACAO_SENHA)
                .tokenHash(HASH)
                .expiraEm(Instant.now().minusSeconds(1))
                .build();
        tokenEncontrado(expirado.getUsuario(), expirado);

        assertThatThrownBy(() -> useCase.executar(TOKEN, NOVA_SENHA))
                .isInstanceOf(TokenRecuperacaoInvalidoException.class);
    }

    @Test
    void deveRecusarNovaSenhaIgualAAtual() {
        Usuario usuario = usuario();
        VerificacaoContato verificacao = verificacaoValida(usuario);
        tokenEncontrado(verificacao.getUsuario(), verificacao);
        when(senhaHasher.confere(NOVA_SENHA, "hash-antigo")).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(TOKEN, NOVA_SENHA))
                .isInstanceOf(SenhaIgualAnteriorException.class);

        verify(sessaoService, never()).revogarTodas(any(), any(), any());
    }
}
