package com.limio.api.modulos.perfil.perfilprestador;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.enums.Uf;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.JanelaAtendimento;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Perfil de atuação do Prestador (TICKET-0054): especialidades, raio, município-base e janelas de
 * atendimento — o que define quais anúncios do mural combinam com ele. No máximo um por
 * {@code usuarioId}, que é UUID cru, nunca relação JPA pro {@code Usuario} de {@code modulos.auth}
 * (ADR-0001 §1.6).
 *
 * Sem setter de propósito: {@code completo} depende de todos os campos de atuação, então eles só mudam
 * juntos, por {@link #atualizarAtuacao}, que recalcula {@code completo} no fim.
 */
@Entity
@Table(name = "perfil_prestador")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerfilPrestador extends BaseEntity {

    @Column(name = "usuario_id", nullable = false, unique = true, updatable = false)
    private UUID usuarioId;

    @Column(name = "raio_atendimento_km", nullable = false)
    private Integer raioAtendimentoKm;

    @Column(name = "municipio_base", nullable = false, length = 100)
    private String municipioBase;

    @Enumerated(EnumType.STRING)
    @Column(name = "uf_base", nullable = false, length = 2)
    private Uf ufBase;

    @Embedded
    @AttributeOverride(name = "latitude", column = @Column(name = "latitude_base", nullable = false))
    @AttributeOverride(name = "longitude", column = @Column(name = "longitude_base", nullable = false))
    private Coordenadas coordenadasBase;

    @Column(name = "completo", nullable = false)
    private boolean completo;

    @ManyToMany
    @JoinTable(name = "prestador_categoria",
            joinColumns = @JoinColumn(name = "perfil_prestador_id"),
            inverseJoinColumns = @JoinColumn(name = "categoria_id"))
    private Set<Categoria> especialidades = new HashSet<>();

    @OneToMany(mappedBy = "perfilPrestador", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JanelaAtendimento> janelas = new ArrayList<>();

    private PerfilPrestador(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    /** Perfil que o Prestador ainda não salvou: tudo vazio, {@code completo = false}. Não persiste. */
    public static PerfilPrestador novo(UUID usuarioId) {
        return new PerfilPrestador(usuarioId);
    }

    /**
     * Substitui o estado de atuação inteiro (especialidades e janelas incluídas) e recalcula
     * {@code completo}. Não persiste — quem chama salva.
     */
    public void atualizarAtuacao(Set<Categoria> novasEspecialidades, int raioAtendimentoKm, String municipioBase,
            Uf ufBase, Coordenadas coordenadasBase, List<JanelaAtendimento> novasJanelas) {
        this.raioAtendimentoKm = raioAtendimentoKm;
        this.municipioBase = municipioBase;
        this.ufBase = ufBase;
        this.coordenadasBase = coordenadasBase;

        // clear + add na mesma coleção (nunca trocar a referência): o Hibernate rastreia a coleção dele, e é
        // assim que o orphanRemoval apaga as janelas antigas e o prestador_categoria perde as linhas tiradas.
        this.especialidades.clear();
        this.especialidades.addAll(novasEspecialidades);
        this.janelas.clear();
        novasJanelas.forEach(janela -> {
            janela.setPerfilPrestador(this);
            this.janelas.add(janela);
        });

        this.completo = calcularCompleto();
    }

    /**
     * Critério PROVISÓRIO — pendência do PO no TICKET-0054; usa o exemplo do card: ao menos 1
     * especialidade + município-base/UF + raio. Janelas de atendimento não entram. Quando o PO travar o
     * critério, a mudança é só aqui.
     */
    private boolean calcularCompleto() {
        return !especialidades.isEmpty()
                && municipioBase != null
                && ufBase != null
                && raioAtendimentoKm != null;
    }
}
