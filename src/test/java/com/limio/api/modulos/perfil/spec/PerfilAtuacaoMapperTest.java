package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.modulos.perfil.actions.mapper.CategoriaMapper;
import com.limio.api.modulos.perfil.actions.mapper.PerfilAtuacaoMapper;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.perfilprestador.Coordenadas;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.enums.Uf;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.JanelaAtendimento;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.enums.DiaSemana;
import com.limio.api.modulos.perfil.records.CategoriaResponse;
import com.limio.api.modulos.perfil.records.JanelaAtendimentoResponse;
import com.limio.api.modulos.perfil.records.PerfilAtuacaoResponse;

class PerfilAtuacaoMapperTest {

    private final PerfilAtuacaoMapper mapper = new PerfilAtuacaoMapper(new CategoriaMapper());

    private static Categoria categoria(String nome, int ordem) {
        Categoria categoria = Categoria.builder().nome(nome).grupo("Casa e reparos").ordem(ordem).ativa(true).build();
        ReflectionTestUtils.setField(categoria, "id", UUID.randomUUID());
        return categoria;
    }

    private static JanelaAtendimento janela(DiaSemana dia, int horaInicio, int horaFim) {
        return JanelaAtendimento.builder()
                .diaSemana(dia)
                .horaInicio(LocalTime.of(horaInicio, 0))
                .horaFim(LocalTime.of(horaFim, 0))
                .build();
    }

    @Test
    void deveDevolverEspecialidadesEmOrdemDeExibicaoEJanelasPorDiaEHorario() {
        PerfilPrestador perfil = PerfilPrestador.novo(UUID.randomUUID());
        perfil.atualizarAtuacao(Set.of(categoria("Pintura", 3), categoria("Elétrica", 1)), 10, "Campinas", Uf.SP,
                new Coordenadas(-22.9, -47.06),
                List.of(janela(DiaSemana.SABADO, 9, 12), janela(DiaSemana.SEGUNDA, 14, 18),
                        janela(DiaSemana.SEGUNDA, 8, 12)));

        PerfilAtuacaoResponse resposta = mapper.toResponse(perfil);

        assertThat(resposta.especialidades()).extracting(CategoriaResponse::nome).containsExactly("Elétrica", "Pintura");
        assertThat(resposta.janelas()).extracting(JanelaAtendimentoResponse::diaSemana, JanelaAtendimentoResponse::horaInicio)
                .containsExactly(
                        tuple(DiaSemana.SEGUNDA, LocalTime.of(8, 0)),
                        tuple(DiaSemana.SEGUNDA, LocalTime.of(14, 0)),
                        tuple(DiaSemana.SABADO, LocalTime.of(9, 0)));
        assertThat(resposta.completo()).isTrue();
    }

    @Test
    void deveDevolverPerfilVazioQuandoOPrestadorNuncaSalvou() {
        PerfilAtuacaoResponse resposta = mapper.toResponse(PerfilPrestador.novo(UUID.randomUUID()));

        assertThat(resposta.especialidades()).isEmpty();
        assertThat(resposta.janelas()).isEmpty();
        assertThat(resposta.raioAtendimentoKm()).isNull();
        assertThat(resposta.municipioBase()).isNull();
        assertThat(resposta.ufBase()).isNull();
        assertThat(resposta.completo()).isFalse();
    }
}
