package com.limio.api.modulos.moderacao.records;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import com.limio.api.modulos.moderacao.denuncia.Denuncia;
import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Campos de formulário de {@code POST /denuncias} ({@code multipart/form-data}, ao lado da parte {@code foto}). O alvo é
 * {@code denunciadoId} e/ou no máximo um contexto: {@code anuncioId}, {@code candidaturaId} ou {@code mensagemId}. Só
 * {@code denunciadoId} é denúncia de perfil. Com um contexto, o denunciado sai dele e {@code denunciadoId} é opcional.
 * A descrição perde os espaços das pontas.
 */
public record RegistrarDenunciaRequest(
        UUID denunciadoId,
        UUID anuncioId,
        UUID candidaturaId,
        UUID mensagemId,
        @NotNull(message = "{denuncia.motivo.obrigatorio}")
        MotivoDenuncia motivo,
        @NotBlank(message = "{denuncia.descricao.obrigatoria}")
        @Size(max = Denuncia.DESCRICAO_MAX, message = "{denuncia.descricao.tamanho}")
        String descricao) {

    public RegistrarDenunciaRequest {
        descricao = descricao == null ? null : descricao.strip();
    }

    @AssertTrue(message = "{denuncia.alvo.obrigatorio}")
    public boolean isAlvoInformado() {
        return denunciadoId != null || quantidadeDeContextos() > 0;
    }

    @AssertTrue(message = "{denuncia.alvo.um-contexto}")
    public boolean isUmContextoNoMaximo() {
        return quantidadeDeContextos() <= 1;
    }

    private long quantidadeDeContextos() {
        return Stream.of(anuncioId, candidaturaId, mensagemId).filter(Objects::nonNull).count();
    }
}
