package com.limio.api.modulos.notificacao.aparelhopush;

import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.notificacao.enums.Plataforma;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Aparelho registrado pra receber push (ETI-112): o token Expo do app no aparelho logado. {@code usuarioId} é UUID
 * cru (ADR-0001 §1.6). O token é único: é do aparelho, não da conta. Gravado só pelo upsert de {@link
 * AparelhoPushRepository#registrar}: se outra conta entrar no mesmo aparelho, o registro passa pra ela.
 */
@Entity
@Table(name = "aparelho_push")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AparelhoPush extends BaseEntity {

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "token", nullable = false, unique = true, updatable = false, length = 255)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "plataforma", nullable = false, updatable = false, length = 10)
    private Plataforma plataforma;
}
