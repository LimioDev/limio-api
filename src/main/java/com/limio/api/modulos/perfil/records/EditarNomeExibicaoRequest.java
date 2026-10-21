package com.limio.api.modulos.perfil.records;

import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code PATCH /conta/perfil-empregador/nome-exibicao}. */
public record EditarNomeExibicaoRequest(
        @NotBlank(message = "{perfilEmpregador.nomeExibicao.obrigatorio}")
        @Size(max = PerfilEmpregador.NOME_EXIBICAO_MAX, message = "{perfilEmpregador.nomeExibicao.tamanho}")
        String nomeExibicao) {

    public EditarNomeExibicaoRequest {
        nomeExibicao = nomeExibicao == null ? null : nomeExibicao.strip();
    }
}
