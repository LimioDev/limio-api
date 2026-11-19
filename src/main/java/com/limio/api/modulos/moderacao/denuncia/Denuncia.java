package com.limio.api.modulos.moderacao.denuncia;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;
import com.limio.api.modulos.moderacao.enums.StatusResolucao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Denúncia que um usuário registra contra outro (ETI-114). Todos os ids, menos {@code evidenciaId}, são UUID crus,
 * nunca relação JPA para entidades de outros módulos (ADR-0001 §1.6). {@code evidenciaId} é a {@code Midia}
 * {@code EVIDENCIA_DENUNCIA}, quando veio foto.
 *
 * <p>Registrar não muda nada fora da denúncia: o serviço, o anúncio e o dinheiro ficam como estão (UC). Ela nasce
 * {@code ABERTA} e entra na fila do admin (US58), que vai cuidar da análise e da decisão.
 */
@Entity
@Table(name = "denuncia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Denuncia extends BaseEntity {

    public static final int DESCRICAO_MAX = 1000;

    /** Número mostrado ao usuário, gerado pelo banco ({@code GENERATED ALWAYS AS IDENTITY}). */
    @Generated
    @Column(name = "numero", insertable = false, updatable = false)
    private Long numero;

    @Column(name = "denunciante_id", nullable = false, updatable = false)
    private UUID denuncianteId;

    @Column(name = "denunciado_id", nullable = false, updatable = false)
    private UUID denunciadoId;

    @Column(name = "anuncio_id", updatable = false)
    private UUID anuncioId;

    @Column(name = "candidatura_id", updatable = false)
    private UUID candidaturaId;

    @Column(name = "mensagem_id", updatable = false)
    private UUID mensagemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", nullable = false, updatable = false, length = 40)
    private MotivoDenuncia motivo;

    @Column(name = "descricao", nullable = false, updatable = false, length = DESCRICAO_MAX)
    private String descricao;

    @Column(name = "evidencia_id", updatable = false)
    private UUID evidenciaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_resolucao", nullable = false, length = 20)
    private StatusResolucao statusResolucao;

    @Column(name = "prazo_analise", nullable = false)
    private Instant prazoAnalise;

    /**
     * Quem chama já resolveu o alvo (o denunciado preenchido e o acesso conferido) e validou o resto. Aqui só o
     * retrato do que foi enviado. Não persiste.
     */
    public static Denuncia registrar(UUID denuncianteId, AlvoDenuncia alvo, MotivoDenuncia motivo, String descricao,
            UUID evidenciaId, Instant prazoAnalise) {
        Denuncia denuncia = new Denuncia();
        denuncia.denuncianteId = denuncianteId;
        denuncia.denunciadoId = alvo.denunciadoId();
        denuncia.anuncioId = alvo.anuncioId();
        denuncia.candidaturaId = alvo.candidaturaId();
        denuncia.mensagemId = alvo.mensagemId();
        denuncia.motivo = motivo;
        denuncia.descricao = descricao;
        denuncia.evidenciaId = evidenciaId;
        denuncia.statusResolucao = StatusResolucao.ABERTA;
        denuncia.prazoAnalise = prazoAnalise;
        return denuncia;
    }
}
