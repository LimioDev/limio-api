package com.limio.api.modulos.anuncio;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

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
 * Agregado raiz do módulo: pedido de serviço publicado por um empregador.
 * {@code contratanteId} e {@code anuncioOrigemId} são colunas cruas (nunca
 * relação JPA pro {@code Usuario} de {@code modulos.auth} nem self-join —
 * ADR-0001 §1.6).
 */
@Entity
@Table(name = "anuncio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Anuncio extends BaseEntity {

    @Column(name = "contratante_id", nullable = false)
    private UUID contratanteId;

    @Column(name = "anuncio_origem_id")
    private UUID anuncioOrigemId;

    @Column(name = "descricao", nullable = false)
    private String descricao;

    @Column(name = "data_hora_pretendida", nullable = false)
    private Instant dataHoraPretendida;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusAnuncio status;

    @Column(name = "bairro_regiao_snapshot")
    private String bairroRegiaoSnapshot;

    @Column(name = "endereco_completo_snapshot")
    private String enderecoCompletoSnapshot;

    @Column(name = "latitude_snapshot")
    private double latitudeSnapshot;

    @Column(name = "longitude_snapshot")
    private double longitudeSnapshot;

    @Column(name = "tamanho_local")
    private String tamanhoLocal;

    @Column(name = "numero_pessoas")
    private int numeroPessoas;

    @Column(name = "observacoes")
    private String observacoes;

    @Column(name = "prazo_validacao_horas")
    private int prazoValidacaoHoras;

    @Column(name = "iniciado_em")
    private Instant iniciadoEm;

    @Column(name = "checkout_em")
    private Instant checkoutEm;

    @Column(name = "validado_em")
    private Instant validadoEm;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;

    /** Status ainda "vivo" — ainda não chegou num estado final (TICKET-0049: candidato a encerramento). */
    public boolean isAberto() {
        return switch (status) {
            case PUBLICADO, EM_ANDAMENTO, AGUARDANDO_VALIDACAO -> true;
            case CONCLUIDO, ENCERRADO -> false;
        };
    }

    /** UC16 (encerrar conta do contratante): tira do mural, não conclui. */
    public void encerrarPorExclusaoDeConta(Instant agora) {
        this.status = StatusAnuncio.ENCERRADO;
        this.encerradoEm = agora;
    }

    /** "Combinado": tem candidatura escolhida e a data combinada é daqui a menos de {@code horas}. */
    public boolean estaAMenosDe(Duration janela, Instant agora) {
        return status == StatusAnuncio.EM_ANDAMENTO && dataHoraPretendida.isBefore(agora.plus(janela));
    }
}
