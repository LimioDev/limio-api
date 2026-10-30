package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.perfil.empregador.MeuPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorRepository;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class PerfilEmpregadorServiceTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private PerfilEmpregadorRepository repository;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    private PerfilEmpregadorService service;

    @BeforeEach
    void montarService() {
        service = new PerfilEmpregadorService(repository, consultaDadosUsuario);
    }

    private static DadosUsuario dados(String nomeCompleto) {
        return new DadosUsuario(nomeCompleto, "maria@example.com", "+5511999998888",
                Instant.parse("2026-10-01T10:00:00Z"), null);
    }

    private static PerfilEmpregador perfil(String nome) {
        return PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao(nome).build();
    }

    @Test
    void perfilExistenteDeveSerDevolvidoComOsDadosSemInserir() {
        DadosUsuario dados = dados("Maria da Silva");
        PerfilEmpregador existente = perfil("Maria");
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(dados));
        when(repository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.of(existente));

        MeuPerfilEmpregador resultado = service.garantirPerfil(USUARIO_ID);

        assertThat(resultado.perfil()).isSameAs(existente);
        assertThat(resultado.dados()).isSameAs(dados);
        verify(repository, never()).inserirSeAusente(any(), anyString());
    }

    @Test
    void perfilAusenteDeveSerCriadoComONomeCompletoDoCadastro() {
        PerfilEmpregador criado = perfil("Maria da Silva");
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(dados("  Maria da Silva  ")));
        when(repository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.empty(), Optional.of(criado));

        MeuPerfilEmpregador resultado = service.garantirPerfil(USUARIO_ID);

        verify(repository).inserirSeAusente(USUARIO_ID, "Maria da Silva");
        assertThat(resultado.perfil()).isSameAs(criado);
    }

    @Test
    void nomeCompletoMaiorQueOLimiteDeveSerTruncadoAoCriar() {
        String nomeLongo = "a".repeat(PerfilEmpregador.NOME_EXIBICAO_MAX + 20);
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(dados(nomeLongo)));
        when(repository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.empty(), Optional.of(perfil("x")));

        service.garantirPerfil(USUARIO_ID);

        verify(repository).inserirSeAusente(USUARIO_ID, "a".repeat(PerfilEmpregador.NOME_EXIBICAO_MAX));
    }

    @Test
    void usuarioInexistenteOuEncerradoDeveFalharSemTocarNoRepository() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.garantirPerfil(USUARIO_ID))
                .isInstanceOf(PerfilEmpregadorNaoEncontradoException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void garantirPerfilParaAtualizarDeveTravarOPerfilExistenteEDevolverOMesmoPerfil() {
        PerfilEmpregador existente = perfil("Maria");
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(dados("Maria da Silva")));
        when(repository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.of(existente));
        when(repository.findByIdParaAtualizar(existente.getId())).thenReturn(Optional.of(existente));

        assertThat(service.garantirPerfilParaAtualizar(USUARIO_ID)).isSameAs(existente);

        verify(repository).findByIdParaAtualizar(existente.getId());
    }

    @Test
    void buscarPerfilExistenteNaoDeveInserirNemConsultarOAuth() {
        PerfilEmpregador existente = perfil("Maria");
        when(repository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.of(existente));

        assertThat(service.buscarPerfilExistente(USUARIO_ID)).isSameAs(existente);

        verifyNoInteractions(consultaDadosUsuario);
        verify(repository, never()).inserirSeAusente(any(), any());
    }

    @Test
    void buscarPerfilExistenteDeveFalharComNotFoundSemInserirQuandoNaoHaPerfil() {
        when(repository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPerfilExistente(USUARIO_ID))
                .isInstanceOf(PerfilEmpregadorNaoEncontradoException.class);

        verify(repository, never()).inserirSeAusente(any(), any());
    }

    @Test
    void travarPerfilExistenteDeveTravarPeloIdSemConsultarOAuth() {
        PerfilEmpregador existente = perfil("Maria");
        when(repository.findByIdParaAtualizar(existente.getId())).thenReturn(Optional.of(existente));

        assertThat(service.travarPerfilExistente(existente.getId())).isSameAs(existente);

        verifyNoInteractions(consultaDadosUsuario);
    }

    @Test
    void garantirPerfilParaAtualizarNaoDeveTravarQuandoAContaFoiEncerrada() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.garantirPerfilParaAtualizar(USUARIO_ID))
                .isInstanceOf(PerfilEmpregadorNaoEncontradoException.class);

        verify(repository, never()).findByIdParaAtualizar(any());
    }

    @Test
    void nomeExibicaoSemCriarDeveUsarOPerfilExistente() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(dados("Maria da Silva")));
        when(repository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.of(perfil("Mari")));

        assertThat(service.nomeExibicaoSemCriar(USUARIO_ID)).contains("Mari");
    }

    @Test
    void nomeExibicaoSemCriarDeveCairNoNomeInicialSemInserirQuandoNaoHaPerfil() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(dados("  Maria da Silva  ")));
        when(repository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThat(service.nomeExibicaoSemCriar(USUARIO_ID)).contains("Maria da Silva");

        verify(repository, never()).inserirSeAusente(any(), any());
    }

    @Test
    void nomeExibicaoSemCriarDeveSerVazioQuandoAContaFoiEncerrada() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThat(service.nomeExibicaoSemCriar(USUARIO_ID)).isEmpty();

        verifyNoInteractions(repository);
    }
}
