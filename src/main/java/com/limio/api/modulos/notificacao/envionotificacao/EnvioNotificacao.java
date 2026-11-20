package com.limio.api.modulos.notificacao.envionotificacao;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.notificacao.enums.CanalNotificacao;
import com.limio.api.modulos.notificacao.enums.StatusEnvio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Por qual canal uma {@code Notificacao} saiu, ou falhou (ETI-112). Subentidade da notificação: uma linha por canal
 * tentado. Canal desligado não gera linha. Imutável.
 */
@Entity
@Table(name = "envio_notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnvioNotificacao extends BaseEntity {

    private static final int TAMANHO_DETALHE = 300;

    @Column(name = "notificacao_id", nullable = false, updatable = false)
    private UUID notificacaoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, updatable = false, length = 20)
    private CanalNotificacao canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 20)
    private StatusEnvio status;

    @Column(name = "ocorrido_em", nullable = false, updatable = false)
    private Instant ocorridoEm;

    /** Motivo da falha, cortado no tamanho da coluna. Nulo quando enviou. */
    @Column(name = "detalhe", updatable = false, length = TAMANHO_DETALHE)
    private String detalhe;

    private EnvioNotificacao(UUID notificacaoId, CanalNotificacao canal, StatusEnvio status, Instant ocorridoEm,
            String detalhe) {
        this.notificacaoId = notificacaoId;
        this.canal = canal;
        this.status = status;
        this.ocorridoEm = ocorridoEm;
        this.detalhe = detalhe == null || detalhe.length() <= TAMANHO_DETALHE ? detalhe
                : detalhe.substring(0, TAMANHO_DETALHE);
    }

    public static EnvioNotificacao enviado(UUID notificacaoId, CanalNotificacao canal, Instant ocorridoEm) {
        return new EnvioNotificacao(notificacaoId, canal, StatusEnvio.ENVIADO, ocorridoEm, null);
    }

    public static EnvioNotificacao falhou(UUID notificacaoId, CanalNotificacao canal, Instant ocorridoEm,
            String detalhe) {
        return new EnvioNotificacao(notificacaoId, canal, StatusEnvio.FALHOU, ocorridoEm, detalhe);
    }
}
