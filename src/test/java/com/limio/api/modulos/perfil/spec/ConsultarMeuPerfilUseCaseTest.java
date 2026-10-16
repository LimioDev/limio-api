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
import com.limio.api.modulos.perfil.actions.usecase.ConsultarMeuPerfilUseCase;
import com.limio.api.modulos.perfil.empregador.MeuPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class ConsultarMeuPerfilUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    private ConsultarMeuPerfilUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsultarMeuPerfilUseCase(consultaDadosUsuario, perfilEmpregadorService);
    }

    private static DadosUsuario dados(String nomeCompleto) {
        return new DadosUsuario(nomeCompleto, "maria@example.com", "+5511999998888", Instant.parse("2026-10-01T10:00:00Z"),
                null);
    }

    @Test
    void deveJuntarPerfilEDadosDoUsuario() {
        DadosUsuario dados = dados("Maria da Silva");
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Maria").build();
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(dados));
        when(perfilEmpregadorService.buscarOuCriar(USUARIO_ID, "Maria da Silva")).thenReturn(perfil);

        MeuPerfilEmpregador resultado = useCase.executar(USUARIO_ID);

        assertThat(resultado.perfil()).isSameAs(perfil);
        assertThat(resultado.dados()).isSameAs(dados);
    }

    @Test
    void deveInicializarOPerfilComONomeCompletoDoCadastro() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(dados("  Maria da Silva  ")));
        when(perfilEmpregadorService.buscarOuCriar(USUARIO_ID, "Maria da Silva"))
                .thenReturn(PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Maria da Silva").build());

        useCase.executar(USUARIO_ID);

        verify(perfilEmpregadorService).buscarOuCriar(USUARIO_ID, "Maria da Silva");
    }

    @Test
    void deveTruncarNomeCompletoMaiorQueOLimiteAoInicializar() {
        String nomeLongo = "a".repeat(PerfilEmpregador.NOME_EXIBICAO_MAX + 20);
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.of(dados(nomeLongo)));
        when(perfilEmpregadorService.buscarOuCriar(any(), anyString()))
                .thenReturn(PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("x").build());

        useCase.executar(USUARIO_ID);

        verify(perfilEmpregadorService).buscarOuCriar(USUARIO_ID, "a".repeat(PerfilEmpregador.NOME_EXIBICAO_MAX));
    }

    @Test
    void deveRecusarEnaoCriarPerfilQuandoUsuarioNaoExisteOuFoiEncerrado() {
        when(consultaDadosUsuario.buscarPorId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID))
                .isInstanceOf(PerfilEmpregadorNaoEncontradoException.class);

        verifyNoInteractions(perfilEmpregadorService);
        verify(perfilEmpregadorService, never()).salvar(any());
    }
}
