package com.limio.api.modulos.notificacao.preferencianotificacao;

import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Canais de notificação ligados/desligados de um usuário (ETI-34) — no máximo
 * um registro por {@code usuarioId}. {@code usuarioId} é UUID cru, nunca
 * relação JPA pro {@code Usuario} de {@code modulos.auth} (ADR-0001 §1.6).
 *
 * Canais são {@link Boolean} (não primitivo): uma instância ainda não salva
 * pode carregar canal sem padrão definido (ver
 * {@code ConsultarPreferenciaNotificacaoUseCase}); no banco os quatro são
 * {@code NOT NULL}.
 */
@Entity
@Table(name = "preferencia_notificacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreferenciaNotificacao extends BaseEntity {

    @Column(name = "usuario_id", nullable = false, unique = true, updatable = false)
    private UUID usuarioId;

    @Column(name = "push", nullable = false)
    private Boolean push;

    @Column(name = "email", nullable = false)
    private Boolean email;

    @Column(name = "sms", nullable = false)
    private Boolean sms;

    @Column(name = "whatsapp", nullable = false)
    private Boolean whatsapp;
}
