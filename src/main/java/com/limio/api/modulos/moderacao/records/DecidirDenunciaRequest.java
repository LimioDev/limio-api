package com.limio.api.modulos.moderacao.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.moderacao.denuncia.DecisaoModeracao;
import com.limio.api.modulos.moderacao.enums.TipoSancao;
import com.limio.api.modulos.moderacao.enums.VereditoModeracao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /admin/denuncias/{id}/decisao} (ETI-116). O fundamento perde os espaços das pontas e é também o
 * motivo da sanção. A coerência entre veredito, sanção, período e avaliação é conferida no usecase.
 *
 * @param sancao            obrigatória no {@code PROCEDENTE}; ausente no {@code IMPROCEDENTE}
 * @param inicioEm          só na {@code SUSPENSAO}
 * @param fimEm             só na {@code SUSPENSAO}, depois do início e no futuro
 * @param avaliacaoId       só em {@code OCULTAR_AVALIACAO}: uma avaliação escrita pelo denunciado
 * @param removerFotoPerfil opcional, só no {@code PROCEDENTE} (UC "Enviar foto de perfil")
 */
public record DecidirDenunciaRequest(
        @NotNull(message = "{moderacao.decisao.veredito.obrigatorio}")
        VereditoModeracao veredito,
        @NotBlank(message = "{moderacao.decisao.fundamento.obrigatorio}")
        @Size(max = DecisaoModeracao.FUNDAMENTO_MAX, message = "{moderacao.decisao.fundamento.tamanho}")
        String fundamento,
        TipoSancao sancao,
        Instant inicioEm,
        Instant fimEm,
        UUID avaliacaoId,
        Boolean removerFotoPerfil) {

    public DecidirDenunciaRequest {
        fundamento = fundamento == null ? null : fundamento.strip();
    }
}
