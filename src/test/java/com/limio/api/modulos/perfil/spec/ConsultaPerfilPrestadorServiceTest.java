package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.comum.portas.ResumoPerfilPrestador;
import com.limio.api.modulos.perfil.actions.service.ConsultaPerfilPrestadorService;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.categoria.CategoriaService;
import com.limio.api.modulos.perfil.geo.Coordenadas;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;

/** Porta de leitura da atuação do Prestador para o mural (ETI-73). */
@ExtendWith(MockitoExtension.class)
class ConsultaPerfilPrestadorServiceTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final Coordenadas CAMPINAS = new Coordenadas(-22.9056, -47.0608);

    @Mock
    private PerfilPrestadorService perfilPrestadorService;

    @Mock
    private CategoriaService categoriaService;

    private ConsultaPerfilPrestadorService service;

    @BeforeEach
    void montarService() {
        service = new ConsultaPerfilPrestadorService(perfilPrestadorService, categoriaService);
    }

    private Categoria categoria(String nome) {
        Categoria categoria = Categoria.builder().nome(nome).grupo("Serviços").ordem(1).build();
        ReflectionTestUtils.setField(categoria, "id", UUID.randomUUID());
        return categoria;
    }

    private PerfilPrestador perfilCompleto(Set<Categoria> especialidades) {
        PerfilPrestador perfil = PerfilPrestador.novo(USUARIO_ID);
        perfil.atualizarAtuacao(especialidades, 15, "Campinas", Uf.SP, CAMPINAS, List.of());
        return perfil;
    }

    @Test
    void dadoPerfilCompleto_quandoBuscaCompleto_entaoDevolveEspecialidadesRaioEBase() {
        Categoria eletrica = categoria("Elétrica");
        Categoria outros = categoria("Outros");
        when(perfilPrestadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(perfilCompleto(Set.of(eletrica))));
        when(categoriaService.buscarOutros()).thenReturn(Optional.of(outros));

        ResumoPerfilPrestador resumo = service.buscarCompleto(USUARIO_ID).orElseThrow();

        assertThat(resumo.especialidadeIds()).containsExactly(eletrica.getId());
        assertThat(resumo.categoriaOutrosId()).isEqualTo(outros.getId());
        assertThat(resumo.raioAtendimentoKm()).isEqualTo(15);
        assertThat(resumo.latitudeBase()).isEqualTo(CAMPINAS.latitude());
        assertThat(resumo.longitudeBase()).isEqualTo(CAMPINAS.longitude());
        assertThat(resumo.possuiOutros()).isFalse();
    }

    @Test
    void dadoPrestadorComOutrosNasEspecialidades_quandoBuscaCompleto_entaoPossuiOutros() {
        Categoria outros = categoria("Outros");
        when(perfilPrestadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(perfilCompleto(Set.of(outros))));
        when(categoriaService.buscarOutros()).thenReturn(Optional.of(outros));

        assertThat(service.buscarCompleto(USUARIO_ID).orElseThrow().possuiOutros()).isTrue();
    }

    @Test
    void dadoUsuarioSemPerfilSalvo_quandoBuscaCompleto_entaoVazio() {
        when(perfilPrestadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());

        assertThat(service.buscarCompleto(USUARIO_ID)).isEmpty();
    }

    @Test
    void dadoPerfilIncompleto_quandoBuscaCompleto_entaoVazio() {
        PerfilPrestador semEspecialidades = perfilCompleto(Set.of());
        when(perfilPrestadorService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(semEspecialidades));

        assertThat(service.buscarCompleto(USUARIO_ID)).isEmpty();
    }
}
