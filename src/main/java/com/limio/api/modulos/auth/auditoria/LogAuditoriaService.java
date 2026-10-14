package com.limio.api.modulos.auth.auditoria;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.auth.auditoria.enums.TipoAcaoAuditoria;

/** Camada técnica de persistência de {@link LogAuditoria} — CRUD genérico (herdado) + limpeza por retenção. */
@Component
public class LogAuditoriaService extends BaseService<LogAuditoria, UUID, LogAuditoriaRepository> {

    public LogAuditoriaService(LogAuditoriaRepository repository) {
        super(repository);
    }

    public LogAuditoria registrar(String entidade, UUID entidadeId, TipoAcaoAuditoria tipoAcao, String origem,
            String ip) {
        return salvar(LogAuditoria.builder()
                .entidade(entidade)
                .entidadeId(entidadeId)
                .tipoAcao(tipoAcao)
                .origem(origem)
                .ip(ip)
                .build());
    }

    /** Apaga entradas com mais do que a retenção de 5 anos (rotina diária — ver {@code LimparRetencaoUseCase}). */
    @Transactional
    public void limparAntigos(Instant limite) {
        repository.apagarAntesDe(limite);
    }
}
