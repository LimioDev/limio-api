package com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.Cobranca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Recibo de uma {@link Cobranca} aprovada (ETI-81, UC "enviar o recibo"), subentidade sem ciclo de vida
 * próprio — nasce junto da cobrança, nunca é editado. {@code numero} reaproveita o id da própria cobrança
 * (já único) em vez de uma numeração sequencial própria, que o card não pede.
 */
@Entity
@Table(name = "documento_fiscal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentoFiscal extends BaseEntity {

    @Column(name = "cobranca_id", nullable = false, unique = true, updatable = false)
    private UUID cobrancaId;

    @Column(name = "numero", nullable = false, updatable = false, length = 100)
    private String numero;

    @Column(name = "emitido_em", nullable = false, updatable = false)
    private Instant emitidoEm;

    private DocumentoFiscal(UUID cobrancaId, String numero, Instant emitidoEm) {
        this.cobrancaId = cobrancaId;
        this.numero = numero;
        this.emitidoEm = emitidoEm;
    }

    public static DocumentoFiscal emitir(Cobranca cobranca, Instant agora) {
        return new DocumentoFiscal(cobranca.getId(), "NF-" + cobranca.getId(), agora);
    }
}
