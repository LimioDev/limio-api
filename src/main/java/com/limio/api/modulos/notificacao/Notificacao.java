package com.limio.api.modulos.notificacao;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.notificacao.enums.TipoNotificacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Notificação mostrada ao usuário na tela 43 (ETI-110). Quem grava é o envio de avisos (ETI-112). {@code usuarioId} e
 * {@code anuncioId} são UUID crus, sem relação JPA com {@code auth} nem {@code anuncio} (ADR-0001 §1.6).
 *
 * <p>Só {@code lidaEm} muda depois de criada, por {@link #marcarComoLida}.
 */
@Entity
@Table(name = "notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Notificacao extends BaseEntity {

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    /** Anúncio ligado, se houver: o app abre o detalhe dele ao tocar na notificação. */
    @Column(name = "anuncio_id", updatable = false)
    private UUID anuncioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 40)
    private TipoNotificacao tipo;

    @Column(name = "titulo", nullable = false, updatable = false, length = 120)
    private String titulo;

    @Column(name = "corpo", nullable = false, updatable = false, length = 500)
    private String corpo;

    @Column(name = "enviada_em", nullable = false, updatable = false)
    private Instant enviadaEm;

    @Column(name = "lida_em")
    private Instant lidaEm;

    public boolean isLida() {
        return lidaEm != null;
    }

    /** Idempotente: abrir de novo uma notificação já lida mantém a data da primeira leitura. */
    public void marcarComoLida(Instant agora) {
        if (!isLida()) {
            lidaEm = agora;
        }
    }
}
