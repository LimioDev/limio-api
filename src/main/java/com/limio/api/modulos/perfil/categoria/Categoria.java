package com.limio.api.modulos.perfil.categoria;

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
 * Especialidade que um Prestador pode escolher (taxonomia de atuação). {@code grupo} e {@code ordem} não
 * estão no DER: a tela "Especialidades" agrupa ("Casa e reparos", "Limpeza", ...) e ordena as categorias
 * (TICKET-0054).
 *
 * {@code protegida} ("Outros") nunca fica inativa — garantido pelo banco
 * ({@code ck_categoria_protegida_ativa}), então "Outros" está sempre na lista de categorias ativas.
 */
@Entity
@Table(name = "categoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria extends BaseEntity {

    @Column(name = "nome", nullable = false, unique = true, length = 100)
    private String nome;

    @Column(name = "grupo", nullable = false, length = 100)
    private String grupo;

    @Column(name = "ordem", nullable = false)
    private Integer ordem;

    @Column(name = "ativa", nullable = false)
    private boolean ativa;

    @Column(name = "protegida", nullable = false)
    private boolean protegida;
}
