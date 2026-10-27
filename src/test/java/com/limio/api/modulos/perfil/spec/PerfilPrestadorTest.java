package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.geo.Coordenadas;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.JanelaAtendimento;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.enums.DiaSemana;

/** Critério provisório de "perfil completo" (pendência do PO): 1+ especialidade + município-base/UF + raio. */
class PerfilPrestadorTest {

    private static final Coordenadas CAMPINAS = new Coordenadas(-22.9056391, -47.059564);

    private static Categoria eletrica() {
        Categoria categoria = Categoria.builder().nome("Elétrica").grupo("Casa e reparos").ordem(1).ativa(true).build();
        ReflectionTestUtils.setField(categoria, "id", UUID.randomUUID());
        return categoria;
    }

    @Test
    void perfilNuncaSalvoNaoEstaCompleto() {
        PerfilPrestador perfil = PerfilPrestador.novo(UUID.randomUUID());

        assertThat(perfil.isCompleto()).isFalse();
        assertThat(perfil.getEspecialidades()).isEmpty();
        assertThat(perfil.getJanelas()).isEmpty();
    }

    @Test
    void comEspecialidadeMunicipioERaioFicaCompletoMesmoSemJanelas() {
        PerfilPrestador perfil = PerfilPrestador.novo(UUID.randomUUID());

        perfil.atualizarAtuacao(Set.of(eletrica()), 10, "Campinas", Uf.SP, CAMPINAS, List.of());

        assertThat(perfil.isCompleto()).isTrue();
    }

    @Test
    void semEspecialidadeFicaIncompleto() {
        PerfilPrestador perfil = PerfilPrestador.novo(UUID.randomUUID());

        perfil.atualizarAtuacao(Set.of(), 10, "Campinas", Uf.SP, CAMPINAS, List.of(JanelaAtendimento.builder()
                .diaSemana(DiaSemana.SABADO)
                .horaInicio(LocalTime.of(9, 0))
                .horaFim(LocalTime.of(13, 0))
                .build()));

        assertThat(perfil.isCompleto()).isFalse();
    }

    @Test
    void tirarAsEspecialidadesDeUmPerfilCompletoOTornaIncompleto() {
        PerfilPrestador perfil = PerfilPrestador.novo(UUID.randomUUID());
        perfil.atualizarAtuacao(Set.of(eletrica()), 10, "Campinas", Uf.SP, CAMPINAS, List.of());

        perfil.atualizarAtuacao(Set.of(), 10, "Campinas", Uf.SP, CAMPINAS, List.of());

        assertThat(perfil.isCompleto()).isFalse();
    }
}
