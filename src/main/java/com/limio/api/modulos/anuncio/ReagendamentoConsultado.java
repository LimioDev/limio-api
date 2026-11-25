package com.limio.api.modulos.anuncio;

import java.time.Instant;

import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;

public record ReagendamentoConsultado(Reagendamento reagendamento, Instant dataVigente) {
}
