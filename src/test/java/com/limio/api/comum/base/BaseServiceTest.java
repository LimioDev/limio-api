package com.limio.api.comum.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.excecao.EntidadeNaoEncontradaException;
import com.limio.api.comum.excecao.enums.CodigoErro;

@ExtendWith(MockitoExtension.class)
class BaseServiceTest {

    static class EntidadeTeste extends BaseEntity {
    }

    interface RepositoryTeste extends BaseRepository<EntidadeTeste, UUID> {
    }

    static class ServicoTeste extends BaseService<EntidadeTeste, UUID, RepositoryTeste> {
        ServicoTeste(RepositoryTeste repository) {
            super(repository);
        }
    }

    @Mock
    private RepositoryTeste repository;

    private ServicoTeste service;

    @BeforeEach
    void montarService() {
        service = new ServicoTeste(repository);
    }

    @Test
    void deveSalvarDelegandoAoRepository() {
        EntidadeTeste entidade = new EntidadeTeste();
        when(repository.save(entidade)).thenReturn(entidade);

        EntidadeTeste salva = service.salvar(entidade);

        assertThat(salva).isSameAs(entidade);
        verify(repository).save(entidade);
    }

    @Test
    void deveBuscarPorIdComSucesso() {
        UUID id = UUID.randomUUID();
        EntidadeTeste entidade = new EntidadeTeste();
        when(repository.findById(id)).thenReturn(Optional.of(entidade));

        assertThat(service.buscarPorIdOuFalhar(id)).isSameAs(entidade);
    }

    @Test
    void deveFalharComErroGenericoQuandoIdNaoExiste() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorIdOuFalhar(id))
                .isInstanceOf(EntidadeNaoEncontradaException.class)
                .satisfies(ex -> assertThat(((EntidadeNaoEncontradaException) ex).getCodigo())
                        .isEqualTo(CodigoErro.ENTIDADE_NAO_ENCONTRADA));
    }

    @Test
    void deveDelegarExistePorIdAoRepository() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);

        assertThat(service.existePorId(id)).isTrue();
    }

    @Test
    void deveDelegarRemocaoAoRepository() {
        EntidadeTeste entidade = new EntidadeTeste();

        service.remover(entidade);

        verify(repository).delete(entidade);
    }
}
