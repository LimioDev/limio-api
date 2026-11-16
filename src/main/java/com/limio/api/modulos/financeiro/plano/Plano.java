package com.limio.api.modulos.financeiro.plano;

import java.time.Instant;
import java.time.ZoneOffset;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.financeiro.plano.enums.CicloPlano;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Catálogo de planos (ETI-81, UC "preço e ciclo parametrizáveis"): preço e ciclo mudam só editando a
 * linha, sem versão nova. {@code codigo} é o identificador estável que o front e o request de assinar
 * usam — {@link #CODIGO_GOLD} é o único plano hoje. Sem regra de negócio própria (catálogo lido, nunca
 * editado por este card): {@code @Builder} só pra montar em teste, igual {@code Categoria}.
 */
@Entity
@Table(name = "plano")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plano extends BaseEntity {

    public static final String CODIGO_GOLD = "GOLD";

    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "preco_centavos", nullable = false)
    private long precoCentavos;

    @Column(name = "moeda", nullable = false, length = 3)
    private String moeda;

    @Enumerated(EnumType.STRING)
    @Column(name = "ciclo", nullable = false, length = 10)
    private CicloPlano ciclo;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    /** Fim do ciclo a partir de {@code inicio} — regra do próprio plano, não do usecase que assina. */
    public Instant fimDoCiclo(Instant inicio) {
        return switch (ciclo) {
            case MENSAL -> inicio.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        };
    }
}
