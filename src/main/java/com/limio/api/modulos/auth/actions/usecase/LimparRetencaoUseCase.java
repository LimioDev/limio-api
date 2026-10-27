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
import com.limio.api.modulos.auth.tentativalogin.TentativaLoginService;

/**
 * Rotina diária de limpeza por retenção (TICKET-0034/ETI-36, LGPD): apaga
 * {@code CpfBloqueado} já liberado, {@code LogAuditoria} com mais de 5 anos e
 * {@code Sessao} encerrada/expirada há mais de 6 meses (Marco Civil da
 * Internet, art. 15) e {@code TentativaLogin} com mais de 30 dias. Cada
 * apagamento é uma transação própria e uma falha numa tabela é logada sem
 * impedir as outras de rodar.
 */
@Service
public class LimparRetencaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(LimparRetencaoUseCase.class);

    private final CpfBloqueadoService cpfBloqueadoService;
    private final LogAuditoriaService logAuditoriaService;
    private final SessaoService sessaoService;
    private final TentativaLoginService tentativaLoginService;
    private final Duration validadeLogAuditoria;
    private final Duration validadeSessao;
    private final Duration validadeTentativaLogin;

    public LimparRetencaoUseCase(CpfBloqueadoService cpfBloqueadoService,
            LogAuditoriaService logAuditoriaService,
            SessaoService sessaoService,
            TentativaLoginService tentativaLoginService,
            @Value("${auth.retencao.log-auditoria.validade}") Duration validadeLogAuditoria,
            @Value("${auth.retencao.sessao.validade}") Duration validadeSessao,
            @Value("${auth.retencao.tentativa-login.validade}") Duration validadeTentativaLogin) {
        this.cpfBloqueadoService = cpfBloqueadoService;
        this.logAuditoriaService = logAuditoriaService;
        this.sessaoService = sessaoService;
        this.tentativaLoginService = tentativaLoginService;
        this.validadeLogAuditoria = validadeLogAuditoria;
        this.validadeSessao = validadeSessao;
        this.validadeTentativaLogin = validadeTentativaLogin;
    }

    @Scheduled(cron = "${auth.retencao.cron}")
    public void executar() {
        Instant agora = Instant.now();
        log.info("limpeza de retenção iniciada");
        isolado("cpf_bloqueado", () -> cpfBloqueadoService.limparLiberados(agora));
        isolado("log_auditoria", () -> logAuditoriaService.limparAntigos(agora.minus(validadeLogAuditoria)));
        isolado("sessao", () -> sessaoService.limparEncerradasOuExpiradas(agora.minus(validadeSessao)));
        isolado("tentativa_login", () -> tentativaLoginService.limparAntigas(agora.minus(validadeTentativaLogin)));
        log.info("limpeza de retenção concluída");
    }

    private static void isolado(String tabela, Runnable limpeza) {
        try {
            limpeza.run();
        } catch (RuntimeException e) {
            log.error("falha na limpeza de retenção de {}; demais tabelas continuam", tabela, e);
        }
    }
}
