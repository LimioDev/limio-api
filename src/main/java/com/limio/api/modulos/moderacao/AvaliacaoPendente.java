package com.limio.api.modulos.moderacao;

import java.time.Instant;

import com.limio.api.comum.portas.ResumoServico;

/**
 * O que a tela "Avaliação" precisa de um serviço (ETI-107). {@code avaliarAte} é {@code null} enquanto o serviço não
 * foi concluído; {@code podeAvaliar} junta as três condições: concluído, dentro da janela e ainda sem avaliação de
 * quem pergunta. {@code outraParte} é {@code null} enquanto nenhum Prestador foi escolhido.
 */
public record AvaliacaoPendente(ResumoServico servico, OutraParte outraParte, Instant avaliarAte,
        boolean podeAvaliar) {
}
