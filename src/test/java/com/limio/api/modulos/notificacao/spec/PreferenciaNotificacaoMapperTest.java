package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.notificacao.actions.mapper.PreferenciaNotificacaoMapper;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacao;
import com.limio.api.modulos.notificacao.records.PreferenciaNotificacaoRequest;
import com.limio.api.modulos.notificacao.records.PreferenciaNotificacaoResponse;

class PreferenciaNotificacaoMapperTest {

    private final PreferenciaNotificacaoMapper mapper = new PreferenciaNotificacaoMapper();

    @Test
    void toEntityDeveCopiarOsQuatroCanaisSemDefinirDono() {
        PreferenciaNotificacao entity = mapper.toEntity(new PreferenciaNotificacaoRequest(true, false, true, false));

        assertThat(entity.getPush()).isTrue();
        assertThat(entity.getEmail()).isFalse();
        assertThat(entity.getSms()).isTrue();
        assertThat(entity.getWhatsapp()).isFalse();
        assertThat(entity.getUsuarioId()).isNull();
    }

    @Test
    void toResponseDeveExporSoOsQuatroCanais() {
        PreferenciaNotificacao entity = PreferenciaNotificacao.builder()
                .usuarioId(UUID.randomUUID()).push(false).email(true).sms(false).whatsapp(true).build();

        assertThat(mapper.toResponse(entity)).isEqualTo(new PreferenciaNotificacaoResponse(false, true, false, true));
    }
}
