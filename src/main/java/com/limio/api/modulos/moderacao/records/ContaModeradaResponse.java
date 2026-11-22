package com.limio.api.modulos.moderacao.records;

import java.util.UUID;

import com.limio.api.comum.portas.enums.SituacaoConta;
import com.limio.api.comum.seguranca.enums.PapelUsuario;

/** Uma conta na moderação (ETI-116): só nome, papel e situação. Nunca telefone, e-mail, CPF nem endereço. */
public record ContaModeradaResponse(UUID id, String nome, PapelUsuario papel, SituacaoConta situacao) {
}
