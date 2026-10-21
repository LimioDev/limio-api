package com.limio.api.modulos.perfil.endereco;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.perfil.enums.Uf;

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

@Entity
@Table(name = "endereco_salvo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnderecoSalvo extends BaseEntity {

    @Column(name = "perfil_empregador_id", nullable = false, updatable = false)
    private UUID perfilEmpregadorId;

    @Column(name = "apelido", nullable = false, length = 60)
    private String apelido;

    @Column(name = "cep", nullable = false, length = 8)
    private String cep;

    @Column(name = "rua", nullable = false, length = 255)
    private String rua;

    @Column(name = "numero", nullable = false, length = 20)
    private String numero;

    @Column(name = "complemento", length = 100)
    private String complemento;

    @Column(name = "bairro_regiao", nullable = false, length = 100)
    private String bairroRegiao;

    @Column(name = "cidade", nullable = false, length = 100)
    private String cidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "uf", nullable = false, length = 2)
    private Uf uf;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "is_padrao", nullable = false)
    private boolean padrao;

    @Column(name = "removido_em")
    private Instant removidoEm;

    public boolean isRemovido() {
        return removidoEm != null;
    }

    public void remover(Instant quando) {
        this.removidoEm = quando;
        this.padrao = false;
    }
}
