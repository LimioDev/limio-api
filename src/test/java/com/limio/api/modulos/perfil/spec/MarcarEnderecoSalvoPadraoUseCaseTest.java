package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.modulos.perfil.actions.usecase.MarcarEnderecoSalvoPadraoUseCase;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;
import com.limio.api.modulos.perfil.excecao.EnderecoSalvoNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class MarcarEnderecoSalvoPadraoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID PERFIL_ID = UUID.randomUUID();
    private static final UUID ENDERECO_ID = UUID.randomUUID();

    @Mock
    private PerfilEmpregadorService perfilEmpregadorService;

    @Mock
    private EnderecoSalvoService enderecoSalvoService;

    private MarcarEnderecoSalvoPadraoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new MarcarEnderecoSalvoPadraoUseCase(perfilEmpregadorService, enderecoSalvoService);
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
    void deveDesmarcarOPadraoAtualAntesDeMarcarONovo() {
        EnderecoSalvo alvo = endereco(false);
        when(enderecoSalvoService.buscarEnderecoAtivoOuFalhar(ENDERECO_ID, PERFIL_ID)).thenReturn(alvo);
        when(enderecoSalvoService.salvar(alvo)).thenReturn(alvo);
        when(enderecoSalvoService.listarEnderecosAtivos(PERFIL_ID)).thenReturn(List.of(alvo));

        List<EnderecoSalvo> lista = useCase.executar(USUARIO_ID, ENDERECO_ID);

        assertThat(lista).containsExactly(alvo);
        assertThat(alvo.isPadrao()).isTrue();
        InOrder ordem = inOrder(enderecoSalvoService);
        ordem.verify(enderecoSalvoService).desmarcarEnderecoPadrao(PERFIL_ID);
        ordem.verify(enderecoSalvoService).salvar(alvo);
        ordem.verify(enderecoSalvoService).listarEnderecosAtivos(PERFIL_ID);
    }

    @Test
    void deveSerIdempotenteQuandoOEnderecoJaEPadrao() {
        EnderecoSalvo alvo = endereco(true);
        when(enderecoSalvoService.buscarEnderecoAtivoOuFalhar(ENDERECO_ID, PERFIL_ID)).thenReturn(alvo);

        when(enderecoSalvoService.listarEnderecosAtivos(PERFIL_ID)).thenReturn(List.of(alvo));

        assertThat(useCase.executar(USUARIO_ID, ENDERECO_ID)).containsExactly(alvo);

        verify(enderecoSalvoService, never()).desmarcarEnderecoPadrao(any());
        verify(enderecoSalvoService, never()).salvar(any());
    }

    @Test
    void naoDeveDesmarcarNemSalvarQuandoOEnderecoNaoExisteFoiRemovidoOuEDeOutroPerfil() {
        when(enderecoSalvoService.buscarEnderecoAtivoOuFalhar(ENDERECO_ID, PERFIL_ID))
                .thenThrow(new EnderecoSalvoNaoEncontradoException());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, ENDERECO_ID))
                .isInstanceOf(EnderecoSalvoNaoEncontradoException.class);

        verify(enderecoSalvoService, never()).desmarcarEnderecoPadrao(any());
        verify(enderecoSalvoService, never()).salvar(any());
    }
}
