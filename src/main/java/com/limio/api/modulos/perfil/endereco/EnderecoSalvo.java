package com.limio.api.modulos.perfil.endereco;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.modulos.perfil.geo.Coordenadas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "endereco_salvo")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EnderecoSalvo extends BaseEntity {

    @Column(name = "perfil_empregador_id", nullable = false, updatable = false)
    private UUID perfilEmpregadorId;

    @Column(name = "apelido", nullable = false, length = 60)
    private String apelido;

    @Column(name = "cep", nullable = false, length = 8)
    private String cep;

    @Column(name = "rua", nullable = false, length = 80)
    private String rua;

    @Column(name = "numero", nullable = false, length = 10)
    private String numero;

    @Column(name = "complemento", length = 40)
    private String complemento;

    @Column(name = "bairro_regiao", nullable = false, length = 50)
    private String bairroRegiao;

    @Column(name = "cidade", nullable = false, length = 50)
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

    public void pertencerA(UUID perfilEmpregadorId) {
        this.perfilEmpregadorId = perfilEmpregadorId;
    }

    public boolean isRemovido() {
        return removidoEm != null;
    }

    /** Coordenadas vêm sempre do geocodificador, nunca do cliente.*/
    public void definirCoordenadas(Coordenadas coordenadas) {
        this.latitude = coordenadas.latitude();
        this.longitude = coordenadas.longitude();
    }

    /** Só rua, número, cidade e UF vão ao provedor; mudar bairro, CEP, complemento ou apelido não move o ponto.*/
    public boolean mesmaLocalizacaoQue(EnderecoSalvo outro) {
        return paraGeocodificar().equals(outro.paraGeocodificar());
    }

    public EnderecoParaGeocodificar paraGeocodificar() {
        return new EnderecoParaGeocodificar(rua, numero, cidade, uf);
    }

    public void substituirDadosPor(EnderecoSalvo novos) {
        this.apelido = novos.apelido;
        this.cep = novos.cep;
        this.rua = novos.rua;
        this.numero = novos.numero;
        this.complemento = novos.complemento;
        this.bairroRegiao = novos.bairroRegiao;
        this.cidade = novos.cidade;
        this.uf = novos.uf;
    }

    public void marcarComoPadrao() {
        this.padrao = true;
    }

    public void desmarcarPadrao() {
        this.padrao = false;
    }

    public void remover(Instant quando) {
        this.removidoEm = quando;
        desmarcarPadrao();
    }
}
