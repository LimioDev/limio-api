package com.limio.api.modulos.auth.actions.usecase;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.modulos.auth.cpfbloqueado.CpfBloqueadoService;
import com.limio.api.modulos.auth.sessao.SessaoService;

/**
 * Rotina diária de limpeza por retenção (TICKET-0034/ETI-36, LGPD): apaga
 * {@code CpfBloqueado} já liberado, {@code LogAuditoria} com mais de 5 anos e
 * {@code Sessao} encerrada/expirada há mais de 6 meses (Marco Civil da
 * Internet, art. 15). Cada apagamento é uma transação própria — uma falha
 * numa tabela não impede as outras duas de rodar.
 */
@Service
public class LimparRetencaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(LimparRetencaoUseCase.class);

    private final CpfBloqueadoService cpfBloqueadoService;
    private final LogAuditoriaService logAuditoriaService;
    private final SessaoService sessaoService;
    private final Duration validadeLogAuditoria;
    private final Duration validadeSessao;

    public LimparRetencaoUseCase(CpfBloqueadoService cpfBloqueadoService,
            LogAuditoriaService logAuditoriaService,
            SessaoService sessaoService,
            @Value("${auth.retencao.log-auditoria.validade}") Duration validadeLogAuditoria,
            @Value("${auth.retencao.sessao.validade}") Duration validadeSessao) {
        this.cpfBloqueadoService = cpfBloqueadoService;
        this.logAuditoriaService = logAuditoriaService;
        this.sessaoService = sessaoService;
        this.validadeLogAuditoria = validadeLogAuditoria;
        this.validadeSessao = validadeSessao;
    }

    @Scheduled(cron = "${auth.retencao.cron}")
    public void executar() {
        Instant agora = Instant.now();
        log.info("limpeza de retenção iniciada");
        cpfBloqueadoService.limparLiberados(agora);
        logAuditoriaService.limparAntigos(agora.minus(validadeLogAuditoria));
        sessaoService.limparEncerradasOuExpiradas(agora.minus(validadeSessao));
        log.info("limpeza de retenção concluída");
    }
}
