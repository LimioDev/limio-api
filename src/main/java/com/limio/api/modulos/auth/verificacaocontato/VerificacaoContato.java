package com.limio.api.modulos.auth.verificacaocontato;

import java.time.Instant;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.auth.usuario.Usuario;
import com.limio.api.modulos.auth.verificacaocontato.enums.CanalVerificacao;
import com.limio.api.modulos.auth.verificacaocontato.enums.FinalidadeVerificacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Token de uso único enviado por um canal de contato, pra confirmar a posse
 * dele — recuperação de senha (TICKET-0033) ou confirmação de e-mail (UC05,
 * TICKET-0047). Emitido no cadastro, na troca de e-mail e no reenvio.
 *
 * Nunca guarda o token em texto puro, só {@code tokenHash} (mesma lógica do
 * refresh token da {@link com.limio.api.modulos.auth.sessao.Sessao}).
 */
@Entity
@Table(name = "verificacao_contato")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificacaoContato extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 20)
    private CanalVerificacao canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "finalidade", nullable = false, length = 30)
    private FinalidadeVerificacao finalidade;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /**
     * SHA-256 do e-mail de destino (D10 do TICKET-0048, aprovada em 2026-10-26). Nulo em token emitido
     * antes da V16: esse continua valendo só pelas outras regras.
     */
    @Column(name = "destino_hash", length = 64)
    private String destinoHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "consumido_em")
    private Instant consumidoEm;

    public boolean isValido(Instant agora) {
        return consumidoEm == null && agora.isBefore(expiraEm);
    }

    /** Vale pro e-mail atual da conta? Quem chama passa o hash ({@code TokenVerificacaoHelper.hashDestino}). */
    public boolean valeParaDestino(String hashDestinoAtual) {
        return destinoHash == null || destinoHash.equals(hashDestinoAtual);
    }

    public void consumir(Instant agora) {
        this.consumidoEm = agora;
    }
}
