package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.excecao.enums.CodigoErro;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoRepository;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;
import com.limio.api.modulos.perfil.excecao.EnderecoSalvoNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class EnderecoSalvoServiceTest {

    @Mock
    private EnderecoSalvoRepository repository;

    private EnderecoSalvoService service;

    private final UUID perfilId = UUID.randomUUID();

    @BeforeEach
    void montarService() {
        service = new EnderecoSalvoService(repository);
    }

    @Test
    void deveListarSoOsEnderecosAtivosDoPerfil() {
        List<EnderecoSalvo> ativos = List.of(EnderecoSalvo.builder().apelido("Casa").build());
        when(repository.findAllByPerfilEmpregadorIdAndRemovidoEmIsNullOrderByPadraoDescApelidoAsc(perfilId))
                .thenReturn(ativos);

        assertThat(service.listarEnderecoAtivos(perfilId)).isSameAs(ativos);
    }

    @Test
    void deveDevolverOEnderecoAtivoDoPerfil() {
        UUID id = UUID.randomUUID();
        EnderecoSalvo endereco = EnderecoSalvo.builder().apelido("Casa").build();
        when(repository.findByIdAndPerfilEmpregadorIdAndRemovidoEmIsNull(id, perfilId))
                .thenReturn(Optional.of(endereco));

        assertThat(service.buscarEnderecoAtivoOuFalhar(id, perfilId)).isSameAs(endereco);
    }

    @Test
    void deveFalharComNaoEncontradoQuandoInexistenteRemovidoOuDeOutroPerfil() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndPerfilEmpregadorIdAndRemovidoEmIsNull(id, perfilId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarEnderecoAtivoOuFalhar(id, perfilId))
                .isInstanceOf(EnderecoSalvoNaoEncontradoException.class)
                .satisfies(ex -> assertThat(((EnderecoSalvoNaoEncontradoException) ex).getCodigo())
                        .isEqualTo(CodigoErro.ENDERECO_NAO_ENCONTRADO));
    }

    @Test
    void deveDelegarAoRepositoryODesmarcarDoPadrao() {
        service.desmarcarEnderecoPadrao(perfilId);

        verify(repository).desmarcarPadrao(org.mockito.ArgumentMatchers.eq(perfilId),
                org.mockito.ArgumentMatchers.any(Instant.class));
    }
}
