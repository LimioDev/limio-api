package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.modulos.perfil.actions.service.ConsultaCategoriaService;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.categoria.CategoriaService;

@ExtendWith(MockitoExtension.class)
class ConsultaCategoriaServiceTest {

    @Mock
    private CategoriaService categoriaService;

    @Test
    void dadoIdsConhecidosEDesconhecidos_quandoBuscaEmLote_entaoMapaSoComOsExistentes() {
        UUID id = UUID.randomUUID();
        Categoria eletrica = Categoria.builder().nome("Elétrica").grupo("Casa e reparos").ordem(1).build();
        ReflectionTestUtils.setField(eletrica, "id", id);
        List<UUID> pedidos = List.of(id, UUID.randomUUID());
        when(categoriaService.buscarPorIds(pedidos)).thenReturn(List.of(eletrica));

        Map<UUID, ResumoCategoria> resumos = new ConsultaCategoriaService(categoriaService).buscarPorIdsEmLote(pedidos);

        assertThat(resumos).containsOnlyKeys(id);
        assertThat(resumos.get(id).nome()).isEqualTo("Elétrica");
    }

    @Test
    void dadoSemIds_quandoBuscaEmLote_entaoMapaVazioSemConsultar() {
        assertThat(new ConsultaCategoriaService(categoriaService).buscarPorIdsEmLote(List.of())).isEmpty();
        verifyNoInteractions(categoriaService);
    }
}
