package com.limio.api.comum.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class LogAuditoriaServiceTest {

    private static final UUID ANUNCIO_ID = UUID.randomUUID();

    @Mock
    private LogAuditoriaRepository repository;

    private LogAuditoriaService service;

    @BeforeEach
    void montarService() {
        service = new LogAuditoriaService(repository, JsonMapper.builder().build());
        when(repository.save(any(LogAuditoria.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private LogAuditoria salvo() {
        ArgumentCaptor<LogAuditoria> captor = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void dadoDetalhes_quandoRegistra_entaoSerializaEGravaODetalhesJsonJuntoDosDemaisCampos() {
        Map<String, String> detalhes = new LinkedHashMap<>();
        detalhes.put("dataAnterior", "2026-11-20T15:00:00Z");
        detalhes.put("dataNova", "2026-11-25T15:00:00Z");

        service.registrar("Anuncio", ANUNCIO_ID, TipoAcaoAuditoria.COMBINADO_CANCELADO_TARDIO,
                "anuncio-cancelar-servico", "203.0.113.7", detalhes);

        LogAuditoria log = salvo();
        assertThat(log.getEntidade()).isEqualTo("Anuncio");
        assertThat(log.getEntidadeId()).isEqualTo(ANUNCIO_ID);
        assertThat(log.getTipoAcao()).isEqualTo(TipoAcaoAuditoria.COMBINADO_CANCELADO_TARDIO);
        assertThat(log.getOrigem()).isEqualTo("anuncio-cancelar-servico");
        assertThat(log.getIp()).isEqualTo("203.0.113.7");
        assertThat(log.getDetalhesJson())
                .isEqualTo("{\"dataAnterior\":\"2026-11-20T15:00:00Z\",\"dataNova\":\"2026-11-25T15:00:00Z\"}");
    }

    @Test
    void dadoRegistroSemDetalhes_quandoRegistra_entaoMantemOComportamentoAnteriorComDetalhesNulo() {
        service.registrar("Anuncio", ANUNCIO_ID, TipoAcaoAuditoria.COMBINADO_CANCELADO, "anuncio-encerrar-conta", null);

        LogAuditoria log = salvo();
        assertThat(log.getTipoAcao()).isEqualTo(TipoAcaoAuditoria.COMBINADO_CANCELADO);
        assertThat(log.getOrigem()).isEqualTo("anuncio-encerrar-conta");
        assertThat(log.getIp()).isNull();
        assertThat(log.getDetalhesJson()).isNull();
    }
}
