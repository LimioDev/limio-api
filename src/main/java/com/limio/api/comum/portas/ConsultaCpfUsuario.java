package com.limio.api.comum.portas;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura do CPF do usuário (ETI-81 — integração Asaas: cadastro de cliente no gateway exige
 * CPF). Separada de {@link ConsultaDadosUsuario} porque CPF é dado sensível que só este consumidor
 * precisa hoje (critério "2+ módulos hoje" do ADR-0001) — não entra em {@link DadosUsuario}, que é lido
 * por módulos sem motivo pra ver CPF.
 */
public interface ConsultaCpfUsuario {

    /**
     * @param usuarioId id do usuário
     * @return o CPF (11 dígitos), ou vazio se o usuário não existe, a conta foi encerrada (CPF apagado,
     *     LGPD) ou nunca teve CPF gravado
     */
    Optional<String> cpfDe(UUID usuarioId);
}
