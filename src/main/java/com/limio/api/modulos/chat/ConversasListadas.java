package com.limio.api.modulos.chat;

import org.springframework.data.domain.Page;

public record ConversasListadas(Page<ConversaResumo> conversas, long totalComNaoLidas) {
}
