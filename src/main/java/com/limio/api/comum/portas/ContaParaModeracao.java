package com.limio.api.comum.portas;

import java.util.UUID;

import com.limio.api.comum.portas.enums.SituacaoConta;
import com.limio.api.comum.seguranca.enums.PapelUsuario;

/**
 * O que o administrador vê de uma conta na moderação (ETI-116): nome de exibição, papel ativo e situação. Sem
 * telefone, e-mail, CPF nem endereço.
 */
public record ContaParaModeracao(UUID usuarioId, String nome, PapelUsuario papel, SituacaoConta situacao) {
}
