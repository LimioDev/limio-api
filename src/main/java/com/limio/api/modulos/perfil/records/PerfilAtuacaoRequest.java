package com.limio.api.modulos.perfil.records;

import java.util.List;
import java.util.UUID;

import com.limio.api.modulos.perfil.perfilprestador.enums.Uf;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PUT /perfil/prestador/atuacao}: estado completo, substitui o perfil inteiro.
 * {@code especialidades} são ids de {@code GET /categorias} e podem vir vazias (UC) — a lista em si é
 * obrigatória, pra campo esquecido não apagar as especialidades sem querer. Mesma lógica pra
 * {@code janelas}. UF fora das 27 oficiais nem desserializa (400 {@code VALIDACAO_FALHOU}).
 * Texto das mensagens em {@code ValidationMessages.properties}.
 */
public record PerfilAtuacaoRequest(
        @NotNull(message = "{perfilAtuacao.especialidades.obrigatorio}")
        List<@NotNull(message = "{perfilAtuacao.especialidades.item}") UUID> especialidades,

        @NotNull(message = "{perfilAtuacao.raioAtendimentoKm.obrigatorio}")
        @Min(value = 1, message = "{perfilAtuacao.raioAtendimentoKm.faixa}")
        @Max(value = 40, message = "{perfilAtuacao.raioAtendimentoKm.faixa}")
        Integer raioAtendimentoKm,

        @NotBlank(message = "{perfilAtuacao.municipioBase.obrigatorio}")
        @Size(max = 100, message = "{perfilAtuacao.municipioBase.tamanho}")
        String municipioBase,

        @NotNull(message = "{perfilAtuacao.ufBase.obrigatoria}")
        Uf ufBase,

        @NotNull(message = "{perfilAtuacao.janelas.obrigatorio}")
        List<@NotNull(message = "{perfilAtuacao.janelas.item}") @Valid JanelaAtendimentoRequest> janelas) {

    public PerfilAtuacaoRequest {
        municipioBase = municipioBase == null ? null : municipioBase.trim();
    }
}
