package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.perfil.actions.usecase.ListarEnderecosSalvosUseCase;
import com.limio.api.modulos.perfil.empregador.MeuPerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class ListarEnderecosSalvosUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID PERFIL_ID = UUID.randomUUID();

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    @Mock
    private EnderecoSalvoService enderecoSalvoService;

    private ListarEnderecosSalvosUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ListarEnderecosSalvosUseCase(perfilEmpregadorService, enderecoSalvoService);
    }

    @Test
    void deveListarSoOsEnderecosAtivosDoPerfilDoUsuarioLogado() {
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Maria").build();
        ReflectionTestUtils.setField(perfil, "id", PERFIL_ID);
        DadosUsuario dados = new DadosUsuario("Maria", "m@example.com", "+5511999998888", Instant.now(), null);
        when(perfilEmpregadorService.garantirPerfil(USUARIO_ID)).thenReturn(new MeuPerfilEmpregador(perfil, dados));
        List<EnderecoSalvo> ativos = List.of(EnderecoSalvo.builder().apelido("Casa").build());
        when(enderecoSalvoService.listarEnderecoAtivos(PERFIL_ID)).thenReturn(ativos);

        assertThat(useCase.executar(USUARIO_ID)).isSameAs(ativos);
    }

    @Test
    void naoDeveListarQuandoAContaFoiEncerrada() {
        when(perfilEmpregadorService.garantirPerfil(USUARIO_ID)).thenThrow(new PerfilEmpregadorNaoEncontradoException());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID))
                .isInstanceOf(PerfilEmpregadorNaoEncontradoException.class);

        verifyNoInteractions(enderecoSalvoService);
    }
}
