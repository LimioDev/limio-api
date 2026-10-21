package com.limio.api.modulos.perfil.actions.mapper;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.JanelaAtendimento;
import com.limio.api.modulos.perfil.records.JanelaAtendimentoRequest;
import com.limio.api.modulos.perfil.records.JanelaAtendimentoResponse;
import com.limio.api.modulos.perfil.records.PerfilAtuacaoResponse;

/**
 * Anti-corruption layer entre {@link PerfilPrestador} e os records HTTP do perfil de atuação. Chamado só
 * pelo controller. Não implementa {@code BaseMapper}: o request não vira um {@code PerfilPrestador} inteiro
 * (o dono vem do token e as especialidades são ids) — só as janelas viram entity pro usecase.
 */
@Component
public class PerfilAtuacaoMapper {

    private static final Comparator<JanelaAtendimento> ORDEM_JANELAS =
            Comparator.comparing(JanelaAtendimento::getDiaSemana).thenComparing(JanelaAtendimento::getHoraInicio);

    private final CategoriaMapper categoriaMapper;

    public PerfilAtuacaoMapper(CategoriaMapper categoriaMapper) {
        this.categoriaMapper = categoriaMapper;
    }

    public List<JanelaAtendimento> toJanelas(List<JanelaAtendimentoRequest> requests) {
        return requests.stream()
                .map(request -> JanelaAtendimento.builder()
                        .diaSemana(request.diaSemana())
                        .horaInicio(request.horaInicio())
                        .horaFim(request.horaFim())
                        .build())
                .toList();
    }

    public PerfilAtuacaoResponse toResponse(PerfilPrestador entity) {
        return new PerfilAtuacaoResponse(
                // Set, sem ordem: ordena aqui (as categorias de GET /categorias já vêm ordenadas do banco).
                categoriaMapper.toResponse(
                        entity.getEspecialidades().stream().sorted(Categoria.ORDEM_EXIBICAO).toList()),
                entity.getRaioAtendimentoKm(),
                entity.getMunicipioBase(),
                entity.getUfBase(),
                entity.getJanelas().stream()
                        .sorted(ORDEM_JANELAS)
                        .map(janela -> new JanelaAtendimentoResponse(
                                janela.getDiaSemana(), janela.getHoraInicio(), janela.getHoraFim()))
                        .toList(),
                entity.isCompleto());
    }
}
