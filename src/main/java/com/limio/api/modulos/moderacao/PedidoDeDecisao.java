package com.limio.api.modulos.moderacao;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.moderacao.enums.TipoSancao;
import com.limio.api.modulos.moderacao.enums.VereditoModeracao;

/**
 * O que o administrador decidiu sobre uma denúncia (ETI-116), já fora do contrato HTTP. A coerência entre os campos é
 * conferida pelo {@code DecidirDenunciaUseCase}.
 *
 * @param sancao            obrigatória no procedente, proibida no improcedente
 * @param inicioEm          só na suspensão
 * @param fimEm             só na suspensão, depois do início e no futuro
 * @param avaliacaoId       só ao ocultar avaliação: uma avaliação escrita pelo denunciado
 * @param removerFotoPerfil só no procedente
 */
public record PedidoDeDecisao(VereditoModeracao veredito, String fundamento, TipoSancao sancao, Instant inicioEm,
        Instant fimEm, UUID avaliacaoId, boolean removerFotoPerfil) {
}
