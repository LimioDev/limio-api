package com.limio.api.comum.auditoria;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.base.BaseService;

import tools.jackson.databind.ObjectMapper;

/** Camada técnica de persistência de {@link LogAuditoria} — CRUD genérico (herdado) + limpeza por retenção. */
@Component
public class LogAuditoriaService extends BaseService<LogAuditoria, UUID, LogAuditoriaRepository> {

    private final ObjectMapper objectMapper;

    public LogAuditoriaService(LogAuditoriaRepository repository, ObjectMapper objectMapper) {
        super(repository);
        this.objectMapper = objectMapper;
    }

    public LogAuditoria registrar(String entidade, UUID entidadeId, TipoAcaoAuditoria tipoAcao, String origem,
            String ip) {
        return registrar(entidade, entidadeId, tipoAcao, origem, ip, null);
    }

    /** @param detalhes pares chave/valor do contexto da ação; vira JSON aqui, quem chama não precisa de Jackson */
    public LogAuditoria registrar(String entidade, UUID entidadeId, TipoAcaoAuditoria tipoAcao, String origem,
            String ip, Map<String, ?> detalhes) {
        return salvar(LogAuditoria.builder()
                .entidade(entidade)
                .entidadeId(entidadeId)
                .tipoAcao(tipoAcao)
                .detalhesJson(detalhes == null ? null : objectMapper.writeValueAsString(detalhes))
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
