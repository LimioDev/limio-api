package com.limio.api.modulos.notificacao.preferencianotificacao;

import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.notificacao.enums.CanalNotificacao;

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
 * Canais são {@link Boolean} (não primitivo), no mesmo tipo do
 * {@code PreferenciaNotificacaoRequest}; no banco os três são
 * {@code NOT NULL}. SMS saiu dos canais (ETI-134): nenhum fluxo do app depende
 * de confirmação por SMS, e o canal nunca chegou a disparar nada.
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

    @Column(name = "whatsapp", nullable = false)
    private Boolean whatsapp;

    /**
     * Padrões de conta nova, pra quem nunca salvou a preferência: Push ligado; E-mail e WhatsApp desligados. WhatsApp
     * fica {@code false} enquanto o cadastro (UC01) não coleta opt-in. Não persiste. Vale pra consulta (ETI-34) e pro
     * envio dos avisos (ETI-112).
     */
    public static PreferenciaNotificacao padraoContaNova(UUID usuarioId) {
        return PreferenciaNotificacao.builder()
                .usuarioId(usuarioId)
                .push(true)
                .email(false)
                .whatsapp(false)
                .build();
    }

    /** Se o usuário ligou {@code canal}. Sem {@code default}: canal novo não compila sem decidir aqui (ETI-112). */
    public boolean ligado(CanalNotificacao canal) {
        return switch (canal) {
            case PUSH -> push;
            case EMAIL -> email;
            case WHATSAPP -> whatsapp;
        };
    }
}
