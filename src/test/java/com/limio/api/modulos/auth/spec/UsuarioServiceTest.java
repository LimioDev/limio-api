package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.limio.api.comum.excecao.EntidadeNaoEncontradaException;
import com.limio.api.modulos.auth.excecao.ContatoNaoPermitidoException;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.usuario.UsuarioRepository;
import com.limio.api.modulos.auth.usuario.UsuarioService;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository repository;

    private UsuarioService service;

    @BeforeEach
    void montarService() {
        service = new UsuarioService(repository);
    }

    @Test
    void salvarComEmailUnicoDeveDevolverOUsuarioGravado() {
        Usuario usuario = Usuario.builder().email("a@example.com").build();
        when(repository.saveAndFlush(usuario)).thenReturn(usuario);

        assertThat(service.salvarComEmailUnico(usuario)).isSameAs(usuario);
    }

    @Test
    void violacaoDaConstraintDeEmailDeveVirarContatoNaoPermitido() {
        Usuario usuario = Usuario.builder().email("a@example.com").build();
        var violacao = new ConstraintViolationException("duplicate key", new SQLException("duplicate key"),
                "uk_usuario_email");
        when(repository.saveAndFlush(usuario)).thenThrow(new DataIntegrityViolationException("erro", violacao));

        assertThatThrownBy(() -> service.salvarComEmailUnico(usuario)).isInstanceOf(ContatoNaoPermitidoException.class);
    }

    @Test
    void violacaoIdentificadaSoPelaMensagemDoBancoTambemDeveVirarContatoNaoPermitido() {
        Usuario usuario = Usuario.builder().email("a@example.com").build();
        when(repository.saveAndFlush(usuario)).thenThrow(new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"uk_usuario_email\""));

        assertThatThrownBy(() -> service.salvarComEmailUnico(usuario)).isInstanceOf(ContatoNaoPermitidoException.class);
    }

    @Test
    void outraViolacaoDeIntegridadeDeveSerRelancada() {
        Usuario usuario = Usuario.builder().email("a@example.com").build();
        var outra = new DataIntegrityViolationException("value too long for type character varying(14)");
        when(repository.saveAndFlush(usuario)).thenThrow(outra);

        assertThatThrownBy(() -> service.salvarComEmailUnico(usuario)).isSameAs(outra);
    }

    @Test
    void buscarParaAtualizarDeveLancarQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdParaAtualizar(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarParaAtualizarOuFalhar(id))
                .isInstanceOf(EntidadeNaoEncontradaException.class);
    }
}
