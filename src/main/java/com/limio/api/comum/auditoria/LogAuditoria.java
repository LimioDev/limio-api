package com.limio.api.comum.auditoria;

import java.util.UUID;

import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.base.BaseEntity;

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
 * resto, ver {@code auth.actions.usecase.LimparRetencaoUseCase}). Começou em
 * {@code modulos.auth} (TICKET-0034, só escritor até então) e subiu pra
 * {@code comum/} no TICKET-0049, quando {@code anuncio} passou a gravar
 * aqui também (critério do ADR-0001: 2+ módulos usando). Não tem FK pra
 * nenhuma entity de propósito: {@code entidadeId} sozinho não identifica
 * ninguém depois que a entidade referenciada for anonimizada/encerrada, e
 * uma FK de verdade exigiria {@code comum/} importar módulo.
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
