package com.limio.api.modulos.perfil.records;

import java.util.List;

import com.limio.api.modulos.perfil.enums.Uf;

/**
 * Perfil de atuação do usuário logado. Prestador que nunca salvou recebe tudo vazio (raio, município e
 * UF nulos, listas vazias) e {@code completo = false}. Especialidades em ordem de exibição; janelas por dia
 * da semana (segunda primeiro) e horário de início.
 */
public record PerfilAtuacaoResponse(
        List<CategoriaResponse> especialidades,
        Integer raioAtendimentoKm,
        String municipioBase,
        Uf ufBase,
        List<JanelaAtendimentoResponse> janelas,
        boolean completo) {
}
