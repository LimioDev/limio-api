package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.modulos.perfil.actions.usecase.EditarEnderecoSalvoUseCase;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;
import com.limio.api.modulos.perfil.enums.Uf;
import com.limio.api.modulos.perfil.excecao.EnderecoSalvoNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class EditarEnderecoSalvoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID PERFIL_ID = UUID.randomUUID();
    private static final UUID ENDERECO_ID = UUID.randomUUID();

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    @Mock
    private EnderecoSalvoService enderecoSalvoService;

    private EditarEnderecoSalvoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new EditarEnderecoSalvoUseCase(perfilEmpregadorService, enderecoSalvoService);
        PerfilEmpregador perfil = PerfilEmpregador.builder().usuarioId(USUARIO_ID).nomeExibicao("Maria").build();
        ReflectionTestUtils.setField(perfil, "id", PERFIL_ID);
        when(perfilEmpregadorService.garantirPerfilParaAtualizar(USUARIO_ID)).thenReturn(perfil);
    }

    private static EnderecoSalvo existente() {
        return EnderecoSalvo.builder().perfilEmpregadorId(PERFIL_ID).apelido("Casa").cep("01310100")
                .rua("Rua A").numero("1").complemento("Fundos").bairroRegiao("Centro").cidade("Santos")
                .uf(Uf.SP).latitude(-23.0).longitude(-46.0).padrao(false).build();
    }

    private static EnderecoSalvo novosDados(boolean padrao) {
        return EnderecoSalvo.builder().apelido("Trabalho").cep("20040020").rua("Rua B").numero("S/N")
                .complemento(null).bairroRegiao("Centro").cidade("Rio de Janeiro").uf(Uf.RJ)
                .latitude(-22.9).longitude(-43.2).padrao(padrao).build();
    }

    @Test
    void deveSubstituirTodosOsCamposEditaveisPreservandoOIdEODono() {
        EnderecoSalvo atual = existente();
        when(enderecoSalvoService.buscarEnderecoAtivoOuFalhar(ENDERECO_ID, PERFIL_ID)).thenReturn(atual);
        when(enderecoSalvoService.salvar(atual)).thenReturn(atual);

        EnderecoSalvo editado = useCase.executar(USUARIO_ID, ENDERECO_ID, novosDados(false));

        assertThat(editado.getPerfilEmpregadorId()).isEqualTo(PERFIL_ID);
        assertThat(editado.getApelido()).isEqualTo("Trabalho");
        assertThat(editado.getCep()).isEqualTo("20040020");
        assertThat(editado.getRua()).isEqualTo("Rua B");
        assertThat(editado.getNumero()).isEqualTo("S/N");
        assertThat(editado.getComplemento()).isNull();
        assertThat(editado.getUf()).isEqualTo(Uf.RJ);
        assertThat(editado.getLatitude()).isEqualTo(-22.9);
        assertThat(editado.getLongitude()).isEqualTo(-43.2);
        verify(enderecoSalvoService, never()).desmarcarEnderecoPadrao(PERFIL_ID);
    }

    @Test
    void deveDesmarcarOPadraoAntesDeCarregarOEnderecoQuandoOsNovosDadosForemPadrao() {
        EnderecoSalvo atual = existente();
        when(enderecoSalvoService.buscarEnderecoAtivoOuFalhar(ENDERECO_ID, PERFIL_ID)).thenReturn(atual);
        when(enderecoSalvoService.salvar(atual)).thenReturn(atual);

        EnderecoSalvo editado = useCase.executar(USUARIO_ID, ENDERECO_ID, novosDados(true));

        assertThat(editado.isPadrao()).isTrue();
        InOrder ordem = inOrder(enderecoSalvoService);
        ordem.verify(enderecoSalvoService).desmarcarEnderecoPadrao(PERFIL_ID);
        ordem.verify(enderecoSalvoService).buscarEnderecoAtivoOuFalhar(ENDERECO_ID, PERFIL_ID);
        ordem.verify(enderecoSalvoService).salvar(atual);
    }

    @Test
    void deveTirarOPadraoQuandoOsNovosDadosNaoForemPadrao() {
        EnderecoSalvo atual = existente();
        atual.setPadrao(true);
        when(enderecoSalvoService.buscarEnderecoAtivoOuFalhar(ENDERECO_ID, PERFIL_ID)).thenReturn(atual);
        when(enderecoSalvoService.salvar(atual)).thenReturn(atual);

        assertThat(useCase.executar(USUARIO_ID, ENDERECO_ID, novosDados(false)).isPadrao()).isFalse();
    }

    @Test
    void naoDeveSalvarQuandoOEnderecoNaoExisteFoiRemovidoOuEDeOutroPerfil() {
        when(enderecoSalvoService.buscarEnderecoAtivoOuFalhar(ENDERECO_ID, PERFIL_ID))
                .thenThrow(new EnderecoSalvoNaoEncontradoException());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, ENDERECO_ID, novosDados(false)))
                .isInstanceOf(EnderecoSalvoNaoEncontradoException.class);

        verify(enderecoSalvoService, never()).salvar(org.mockito.ArgumentMatchers.any());
    }
}
