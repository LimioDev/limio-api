package com.limio.api.modulos.chat;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.chat.enums.MotivoSomenteLeitura;
import com.limio.api.modulos.chat.enums.PapelNoChat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Conversa 1:1 entre o Empregador dono do anúncio e o Prestador de uma candidatura (ETI-101). Nasce com a candidatura
 * e nunca é apagada: quando a candidatura ou o anúncio encerram, fica somente leitura e o histórico continua.
 * {@code aplicacaoId}, {@code anuncioId} e os ids das partes são colunas cruas (ADR-0001 §1.6), copiadas do
 * {@code CandidaturaCriadaEvent}.
 */
@Entity
@Table(name = "chat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chat extends BaseEntity {

    @Column(name = "aplicacao_id", nullable = false, updatable = false)
    private UUID aplicacaoId;

    @Column(name = "anuncio_id", nullable = false, updatable = false)
    private UUID anuncioId;

    @Column(name = "contratante_id", nullable = false, updatable = false)
    private UUID contratanteId;

    @Column(name = "prestador_id", nullable = false, updatable = false)
    private UUID prestadorId;

    @Column(name = "somente_leitura", nullable = false)
    private boolean somenteLeitura;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_somente_leitura", length = 40)
    private MotivoSomenteLeitura motivoSomenteLeitura;

    @Column(name = "somente_leitura_em")
    private Instant somenteLeituraEm;

    public static Chat daCandidatura(UUID aplicacaoId, UUID anuncioId, UUID contratanteId, UUID prestadorId) {
        return Chat.builder()
                .aplicacaoId(aplicacaoId)
                .anuncioId(anuncioId)
                .contratanteId(contratanteId)
                .prestadorId(prestadorId)
                .build();
    }

    public boolean participa(UUID usuarioId) {
        return contratanteId.equals(usuarioId) || prestadorId.equals(usuarioId);
    }

    /** Id de quem está do outro lado da conversa para {@code usuarioId}, que precisa ser uma das partes. */
    public UUID outraParte(UUID usuarioId) {
        return contratanteId.equals(usuarioId) ? prestadorId : contratanteId;
    }

    /** Papel de quem está do outro lado: o Empregador vê o "Candidato", o Prestador vê o Empregador. */
    public PapelNoChat papelDaOutraParte(UUID usuarioId) {
        return contratanteId.equals(usuarioId) ? PapelNoChat.CANDIDATO : PapelNoChat.EMPREGADOR;
    }

    /** O primeiro motivo fica: um evento posterior (ex.: anúncio cancelado depois da recusa) não reescreve a tela. */
    public void deixarSomenteLeitura(MotivoSomenteLeitura motivo, Instant agora) {
        if (somenteLeitura)
            return;
        this.somenteLeitura = true;
        this.motivoSomenteLeitura = motivo;
        this.somenteLeituraEm = agora;
    }
}
