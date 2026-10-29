package com.limio.api.modulos.chat.mensagem;

import java.time.Instant;
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
 * Mensagem de um {@code Chat}. {@code texto} é o que o remetente escreveu e {@code textoExibido} o que a outra parte
 * vê: iguais até o mascaramento de contato (US50) entrar. {@code entregueEm} e {@code lidaEm} são preenchidos pela
 * outra parte, uma vez só, direto no banco ({@code MensagemRepository#marcarEntregues}/{@code #marcarLidas}). Lida
 * implica entregue.
 */
@Entity
@Table(name = "mensagem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mensagem extends BaseEntity {

    @Column(name = "chat_id", nullable = false, updatable = false)
    private UUID chatId;

    @Column(name = "remetente_id", nullable = false, updatable = false)
    private UUID remetenteId;

    @Column(name = "texto", nullable = false, updatable = false)
    private String texto;

    @Column(name = "texto_exibido", nullable = false)
    private String textoExibido;

    @Column(name = "data_envio", nullable = false, updatable = false)
    private Instant dataEnvio;

    @Column(name = "entregue_em")
    private Instant entregueEm;

    @Column(name = "lida_em")
    private Instant lidaEm;

    public static Mensagem enviada(UUID chatId, UUID remetenteId, String texto, Instant agora) {
        return Mensagem.builder()
                .chatId(chatId)
                .remetenteId(remetenteId)
                .texto(texto)
                .textoExibido(texto)
                .dataEnvio(agora)
                .build();
    }
}
