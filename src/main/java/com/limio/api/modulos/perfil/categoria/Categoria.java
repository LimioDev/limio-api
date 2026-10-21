package com.limio.api.modulos.perfil.categoria;

import java.util.Comparator;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.perfil.excecao.CategoriaProtegidaException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Especialidade que um Prestador pode escolher (taxonomia de atuação). {@code grupo} e {@code ordem} não
 * estão no DER: a tela "Especialidades" agrupa ("Casa e reparos", "Limpeza", ...) e ordena as categorias
 * (TICKET-0054).
 *
 * {@code protegida} ("Outros") nunca fica inativa, então "Outros" está sempre na lista de categorias
 * ativas. Por isso {@code ativa} e {@code protegida} não têm setter: {@link #desativar} recusa com exceção
 * de negócio, e o banco ({@code ck_categoria_protegida_ativa}) segura quem passar por fora da entity.
 */
@Entity
@Table(name = "categoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria extends BaseEntity {

    /**
     * Ordem de exibição ({@code ordem}, depois nome) — a mesma do {@code ORDER BY} de
     * {@code CategoriaRepository.findByAtivaTrueOrderByOrdemAscNomeAsc}; serve pra coleção que vem sem ordem
     * (as especialidades do perfil são um {@code Set}).
     */
    public static final Comparator<Categoria> ORDEM_EXIBICAO =
            Comparator.comparing(Categoria::getOrdem).thenComparing(Categoria::getNome);

    @Column(name = "nome", nullable = false, unique = true, length = 100)
    private String nome;

    @Column(name = "grupo", nullable = false, length = 100)
    private String grupo;

    @Column(name = "ordem", nullable = false)
    private Integer ordem;

    @Setter(AccessLevel.NONE)
    @Column(name = "ativa", nullable = false)
    private boolean ativa;

    @Setter(AccessLevel.NONE)
    @Column(name = "protegida", nullable = false)
    private boolean protegida;

    public void ativar() {
        this.ativa = true;
    }

    /** @throws CategoriaProtegidaException categoria protegida ("Outros") */
    public void desativar() {
        if (protegida) {
            throw new CategoriaProtegidaException();
        }
        this.ativa = false;
    }
}
