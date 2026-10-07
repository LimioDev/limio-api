package com.limio.api.modulos.auth.sessao;

import java.time.Instant;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.auth.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Login de um usuário em um aparelho. Cada aparelho tem a sua: logout revoga
 * só esta, as dos outros aparelhos continuam valendo.
 *
 * O refresh token nunca é guardado — só o SHA-256 dele
 * ({@code refreshTokenHash}). A cada renovação o hash é trocado, então o
 * refresh token anterior deixa de valer. {@code expiraEm} não está no
 * diagrama de classes: foi acrescentado no TICKET-0032 pra dar validade ao
 * refresh token.
 */
@Entity
@Table(name = "sessao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sessao extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "dispositivo")
    private String dispositivo;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "ultimo_uso_em", nullable = false)
    private Instant ultimoUsoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "revogada_em")
    private Instant revogadaEm;

    public boolean isAtiva(Instant agora) {
        return revogadaEm == null && agora.isBefore(expiraEm);
    }
}
