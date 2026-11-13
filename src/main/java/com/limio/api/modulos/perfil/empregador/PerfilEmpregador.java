package com.limio.api.modulos.perfil.empregador;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.comum.texto.NomeHelper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code @DynamicUpdate}: o UPDATE leva só as colunas alteradas. A média (ETI-107) e o nome de exibição mudam por
 * caminhos diferentes, e um não pode regravar o valor antigo do outro.
 */
@Entity
@Table(name = "perfil_empregador")
@DynamicUpdate
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

    /** Conta encerrada (UC10): o nome de exibição deixa de identificar a pessoa. */
    public static final String NOME_CONTA_ENCERRADA = NomeHelper.CONTA_ENCERRADA;

    /** Média e quantidade recalculadas por {@code moderacao} (ETI-107); este perfil só guarda. */
    public void registrarAvaliacoes(BigDecimal mediaAvaliacoes, int qtdAvaliacoes) {
        this.mediaAvaliacoes = mediaAvaliacoes;
        this.qtdAvaliacoes = qtdAvaliacoes;
    }

    public void anonimizar() {
        this.nomeExibicao = NOME_CONTA_ENCERRADA;
    }

    public static String nomeExibicaoInicialDe(String nomeCompleto) {
        String nome = nomeCompleto.strip();
        return nome.length() <= NOME_EXIBICAO_MAX
                ? nome
                : nome.substring(0, NOME_EXIBICAO_MAX).stripTrailing();
    }
}
