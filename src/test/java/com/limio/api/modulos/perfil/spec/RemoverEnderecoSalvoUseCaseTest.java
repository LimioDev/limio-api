package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.modulos.perfil.actions.usecase.RemoverEnderecoSalvoUseCase;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;
import com.limio.api.modulos.perfil.enums.Uf;
import com.limio.api.modulos.perfil.excecao.EnderecoSalvoNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class RemoverEnderecoSalvoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID PERFIL_ID = UUID.randomUUID();
    private static final UUID ENDERECO_ID = UUID.randomUUID();

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    @Mock
    private EnderecoSalvoService enderecoSalvoService;

    private RemoverEnderecoSalvoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new RemoverEnderecoSalvoUseCase(perfilEmpregadorService, enderecoSalvoService);
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Maria").build();
        ReflectionTestUtils.setField(perfil, "id", PERFIL_ID);
        when(perfilEmpregadorService.garantirPerfilParaAtualizar(USUARIO_ID)).thenReturn(perfil);
    }

    private static EnderecoSalvo endereco(boolean padrao) {
        return EnderecoSalvo.builder().perfilEmpregadorId(PERFIL_ID).apelido("Casa").cep("01310100")
                .rua("Rua A").numero("1").bairroRegiao("Centro").cidade("Santos").uf(Uf.SP)
                .latitude(-23.0).longitude(-46.0).padrao(padrao).build();
    }

    @Test
    void deveMarcarRemovidoEmESalvarSemApagarAFisicamente() {
        EnderecoSalvo atual = endereco(false);
        when(enderecoSalvoService.buscarEnderecoAtivoOuFalhar(ENDERECO_ID, PERFIL_ID)).thenReturn(atual);
        Instant antes = Instant.now();

        useCase.executar(USUARIO_ID, ENDERECO_ID);

        assertThat(atual.isRemovido()).isTrue();
        assertThat(atual.getRemovidoEm()).isBetween(antes, Instant.now());
        InOrder ordem = inOrder(perfilEmpregadorService, enderecoSalvoService);
        ordem.verify(perfilEmpregadorService).garantirPerfilParaAtualizar(USUARIO_ID);
        ordem.verify(enderecoSalvoService).buscarEnderecoAtivoOuFalhar(ENDERECO_ID, PERFIL_ID);
        ordem.verify(enderecoSalvoService).salvar(atual);
        verify(enderecoSalvoService, never()).remover(any());
    }

    @Test
    void deveLimparOPadraoAoRemoverOEnderecoPadrao() {
        EnderecoSalvo atual = endereco(true);
        when(enderecoSalvoService.buscarEnderecoAtivoOuFalhar(ENDERECO_ID, PERFIL_ID)).thenReturn(atual);

        useCase.executar(USUARIO_ID, ENDERECO_ID);

        assertThat(atual.isPadrao()).isFalse();
        assertThat(atual.isRemovido()).isTrue();
        verify(enderecoSalvoService, never()).desmarcarEnderecoPadrao(any());
    }

    @Test
    void naoDeveSalvarQuandoOEnderecoNaoExisteFoiRemovidoOuEDeOutroPerfil() {
        when(enderecoSalvoService.buscarEnderecoAtivoOuFalhar(ENDERECO_ID, PERFIL_ID))
                .thenThrow(new EnderecoSalvoNaoEncontradoException());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, ENDERECO_ID))
                .isInstanceOf(EnderecoSalvoNaoEncontradoException.class);

        verify(enderecoSalvoService, never()).salvar(any());
    }
}
