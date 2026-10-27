package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
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
import com.limio.api.modulos.auth.excecao.TokenRecuperacaoInvalidoException;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioService;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;
import com.limio.api.modulos.auth.verificacaocontato.ConsumoTokenVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.UsuarioEVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContato;
import com.limio.api.modulos.auth.verificacaocontato.VerificacaoContatoService;
import com.limio.api.modulos.auth.verificacaocontato.enums.CanalVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

@ExtendWith(MockitoExtension.class)
class ConsumoTokenVerificacaoTest {

    private static final String TOKEN = "token-de-recuperacao";
    private static final String HASH = TokenVerificacaoHelper.hash(TOKEN);
    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final FinalidadeVerificacao FINALIDADE = FinalidadeVerificacao.RECUPERACAO_SENHA;
    private static final Instant AGORA = Instant.now();

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private VerificacaoContatoService verificacaoContatoService;

    private ConsumoTokenVerificacao consumoTokenVerificacao;

    @BeforeEach
    void montar() {
        consumoTokenVerificacao = new ConsumoTokenVerificacao(usuarioService, verificacaoContatoService);
    }

    private Usuario usuario() {
        return Usuario.builder()
                .email("maria@example.com")
                .papelAtivo(PapelUsuario.EMPREGADOR)
                .statusConta(StatusConta.ATIVA)
                .build();
    }

    private VerificacaoContato verificacaoValida(Usuario usuario) {
        return VerificacaoContato.builder()
                .usuario(usuario)
                .canal(CanalVerificacao.EMAIL)
                .finalidade(FINALIDADE)
                .tokenHash(HASH)
                .expiraEm(AGORA.plus(Duration.ofHours(1)))
                .build();
    }

    private void tokenEncontrado(Usuario usuario, VerificacaoContato verificacao) {
        when(verificacaoContatoService.buscarUsuarioIdPorTokenHash(HASH, FINALIDADE)).thenReturn(Optional.of(USUARIO_ID));
        when(usuarioService.buscarParaAtualizarOuFalhar(USUARIO_ID)).thenReturn(usuario);
        when(verificacaoContatoService.buscarParaConsumir(HASH, FINALIDADE)).thenReturn(Optional.of(verificacao));
    }

    @Test
    void devolveAContaEOTokenQuandoTudoValido() {
        Usuario usuario = usuario();
        VerificacaoContato verificacao = verificacaoValida(usuario);
        tokenEncontrado(usuario, verificacao);

        UsuarioEVerificacao resultado = consumoTokenVerificacao.consumirValido(TOKEN, FINALIDADE, AGORA,
                TokenRecuperacaoInvalidoException::new);

        assertThat(resultado.usuario()).isEqualTo(usuario);
        assertThat(resultado.verificacao()).isEqualTo(verificacao);
    }

    @Test
    void travaAContaAntesDeRelerOToken() {
        Usuario usuario = usuario();
        tokenEncontrado(usuario, verificacaoValida(usuario));

        consumoTokenVerificacao.consumirValido(TOKEN, FINALIDADE, AGORA, TokenRecuperacaoInvalidoException::new);

        InOrder ordem = inOrder(usuarioService, verificacaoContatoService);
        ordem.verify(usuarioService).buscarParaAtualizarOuFalhar(USUARIO_ID);
        ordem.verify(verificacaoContatoService).buscarParaConsumir(HASH, FINALIDADE);
    }

    @Test
    void recusaTokenInexistente() {
        when(verificacaoContatoService.buscarUsuarioIdPorTokenHash(HASH, FINALIDADE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consumoTokenVerificacao.consumirValido(TOKEN, FINALIDADE, AGORA,
                TokenRecuperacaoInvalidoException::new)).isInstanceOf(TokenRecuperacaoInvalidoException.class);
    }

    @Test
    void recusaTokenExpirado() {
        Usuario usuario = usuario();
        VerificacaoContato expirado = VerificacaoContato.builder()
                .usuario(usuario)
                .canal(CanalVerificacao.EMAIL)
                .finalidade(FINALIDADE)
                .tokenHash(HASH)
                .expiraEm(AGORA.minusSeconds(1))
                .build();
        tokenEncontrado(usuario, expirado);

        assertThatThrownBy(() -> consumoTokenVerificacao.consumirValido(TOKEN, FINALIDADE, AGORA,
                TokenRecuperacaoInvalidoException::new)).isInstanceOf(TokenRecuperacaoInvalidoException.class);
    }

    @Test
    void recusaTokenJaConsumido() {
        Usuario usuario = usuario();
        VerificacaoContato consumido = verificacaoValida(usuario);
        consumido.consumir(AGORA);
        tokenEncontrado(usuario, consumido);

        assertThatThrownBy(() -> consumoTokenVerificacao.consumirValido(TOKEN, FINALIDADE, AGORA,
                TokenRecuperacaoInvalidoException::new)).isInstanceOf(TokenRecuperacaoInvalidoException.class);
    }

    @Test
    void recusaContaEncerrada() {
        Usuario encerrada = usuario();
        encerrada.setAnonimizadoEm(AGORA);
        tokenEncontrado(encerrada, verificacaoValida(encerrada));

        assertThatThrownBy(() -> consumoTokenVerificacao.consumirValido(TOKEN, FINALIDADE, AGORA,
                TokenRecuperacaoInvalidoException::new)).isInstanceOf(TokenRecuperacaoInvalidoException.class);
    }

    @Test
    void recusaTokenEmitidoParaOutroEmail() {
        Usuario usuario = usuario();
        VerificacaoContato paraOutro = verificacaoValida(usuario);
        paraOutro.setDestinoHash(TokenVerificacaoHelper.hashDestino("antigo@example.com"));
        tokenEncontrado(usuario, paraOutro);

        assertThatThrownBy(() -> consumoTokenVerificacao.consumirValido(TOKEN, FINALIDADE, AGORA,
                TokenRecuperacaoInvalidoException::new)).isInstanceOf(TokenRecuperacaoInvalidoException.class);
    }
}
