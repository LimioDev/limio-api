package com.limio.api.modulos.perfil.records;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.limio.api.modulos.perfil.perfilprestador.enums.Uf;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.enums.DiaSemana;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PUT /perfil/prestador/atuacao}: estado completo, substitui o perfil inteiro.
 * {@code especialidades} são ids de {@code GET /categorias} e podem vir vazias (UC) — a lista em si é
 * obrigatória, pra campo esquecido não apagar as especialidades sem querer. Mesma lógica pra
 * {@code janelas}, que também não podem se sobrepor no mesmo dia. UF fora das 27 oficiais nem
 * desserializa (400 {@code VALIDACAO_FALHOU}). Texto das mensagens em {@code ValidationMessages.properties}.
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

    /**
     * Janelas do mesmo dia não se sobrepõem (repetida conta como sobreposta). Encostar pode: 08:00-12:00 e
     * 12:00-14:00 passam. Janela nula, sem horário ou com início depois do fim já é recusada pela própria
     * validação dela — aqui fica de fora pra não acusar duas vezes. {@code @JsonIgnore}: é regra de
     * validação, não campo do contrato.
     */
    @JsonIgnore
    @AssertTrue(message = "{perfilAtuacao.janelas.sobreposicao}")
    public boolean isJanelasSemSobreposicao() {
        if (janelas == null) {
            return true;
        }
        Map<DiaSemana, List<JanelaAtendimentoRequest>> porDia = janelas.stream()
                .filter(Objects::nonNull)
                .filter(janela -> janela.diaSemana() != null && janela.horaInicio() != null
                        && janela.horaFim() != null && janela.isIntervaloValido())
                .collect(Collectors.groupingBy(JanelaAtendimentoRequest::diaSemana));
        return porDia.values().stream().allMatch(PerfilAtuacaoRequest::semSobreposicao);
    }

    private static boolean semSobreposicao(List<JanelaAtendimentoRequest> janelasDoDia) {
        List<JanelaAtendimentoRequest> ordenadas = janelasDoDia.stream()
                .sorted(Comparator.comparing(JanelaAtendimentoRequest::horaInicio))
                .toList();
        for (int i = 1; i < ordenadas.size(); i++) {
            if (ordenadas.get(i).horaInicio().isBefore(ordenadas.get(i - 1).horaFim())) {
                return false;
            }
        }
        return true;
    }
}
