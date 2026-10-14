package com.limio.api.modulos.auth.auditoria;

import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.auth.auditoria.enums.TipoAcaoAuditoria;

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
 * Trilha de auditoria — retida por 5 anos (rotina de limpeza diária apaga o
 * resto, ver {@code LimparRetencaoUseCase}). Só {@code modulos.auth} grava
 * aqui hoje; fica neste módulo em vez de {@code comum/} até um segundo módulo
 * precisar (critério do ADR-0001) — não tem FK pra {@code Usuario}
 * de propósito: {@code entidadeId} sozinho não identifica ninguém depois que a
 * entidade referenciada for anonimizada.
 */
@Entity
@Table(name = "log_auditoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogAuditoria extends BaseEntity {

    @Column(name = "entidade", nullable = false, length = 100)
    private String entidade;

    @Column(name = "entidade_id")
    private UUID entidadeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_acao", nullable = false, length = 50)
    private TipoAcaoAuditoria tipoAcao;

    @Column(name = "detalhes_json")
    private String detalhesJson;

    @Column(name = "origem", length = 100)
    private String origem;

    @Column(name = "ip", length = 45)
    private String ip;
}
