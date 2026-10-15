package com.limio.api.modulos.perfil.empregador;

import java.math.BigDecimal;
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

@Entity
@Table(name = "perfil_empregador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerfilEmpregador extends BaseEntity {

    public static final int NOME_EXIBICAO_MAX = 80;

    @Column(name = "usuario_id", nullable = false, unique = true, updatable = false)
    private UUID usuarioId;

    @Column(name = "nome_exibicao", nullable = false, length = NOME_EXIBICAO_MAX)
    private String nomeExibicao;

    @Column(name = "media_avaliacoes", precision = 2, scale = 1)
    private BigDecimal mediaAvaliacoes;

    @Column(name = "qtd_avaliacoes", nullable = false)
    @Builder.Default
    private int qtdAvaliacoes = 0;
}
