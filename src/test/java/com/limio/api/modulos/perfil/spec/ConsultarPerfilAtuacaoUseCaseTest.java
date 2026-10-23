package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.modulos.perfil.actions.usecase.ConsultarPerfilAtuacaoUseCase;
import com.limio.api.modulos.perfil.enums.Uf;
import com.limio.api.modulos.perfil.perfilprestador.Coordenadas;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;

@ExtendWith(MockitoExtension.class)
class ConsultarPerfilAtuacaoUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private PerfilPrestadorService perfilPrestadorService;

    private ConsultarPerfilAtuacaoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsultarPerfilAtuacaoUseCase(perfilPrestadorService);
    }

    @Test
    void deveDevolverOPerfilSalvo() {
        PerfilPrestador salvo = PerfilPrestador.novo(USUARIO_ID);
        salvo.atualizarAtuacao(Set.of(), 15, "Campinas", Uf.SP, new Coordenadas(-22.9, -47.06), List.of());
        when(perfilPrestadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(salvo));

        assertThat(useCase.executar(USUARIO_ID)).isSameAs(salvo);
    }

    @Test
    void deveDevolverPerfilVazioEIncompletoSemPersistirQuandoNuncaSalvou() {
        when(perfilPrestadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());

        PerfilPrestador perfil = useCase.executar(USUARIO_ID);

        assertThat(perfil.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(perfil.getRaioAtendimentoKm()).isNull();
        assertThat(perfil.getMunicipioBase()).isNull();
        assertThat(perfil.isCompleto()).isFalse();
        verify(perfilPrestadorService, never()).salvar(any());
    }
}
