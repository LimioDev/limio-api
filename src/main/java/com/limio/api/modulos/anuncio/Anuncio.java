package com.limio.api.modulos.anuncio;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.comum.portas.EnderecoLocalizado;
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
 * {@code contratanteId}, {@code anuncioOrigemId}, {@code categoriaId} e
 * {@code enderecoSalvoId} são colunas cruas (nunca relação JPA pro
 * {@code Usuario} de {@code modulos.auth}, pra {@code Categoria} ou
 * {@code EnderecoSalvo} de {@code modulos.perfil}, nem self-join —
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

    @Column(name = "categoria_id", nullable = false)
    private UUID categoriaId;

    /** Endereço salvo escolhido na criação; {@code null} com endereço avulso. O que vale é o retrato (*Snapshot).*/
    @Column(name = "endereco_salvo_id")
    private UUID enderecoSalvoId;

    @Column(name = "descricao", nullable = false)
    private String descricao;

    @Column(name = "data_hora_pretendida", nullable = false)
    private Instant dataHoraPretendida;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusAnuncio status;

    @Column(name = "bairro_regiao_snapshot")
    private String bairroRegiaoSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "uf_snapshot", length = 2)
    private Uf ufSnapshot;

    @Column(name = "endereco_completo_snapshot")
    private String enderecoCompletoSnapshot;

    @Column(name = "latitude_snapshot")
    private Double latitudeSnapshot;

    @Column(name = "longitude_snapshot")
    private Double longitudeSnapshot;

    @Column(name = "tamanho_local")
    private String tamanhoLocal;

    @Column(name = "numero_pessoas")
    private Integer numeroPessoas;

    @Column(name = "observacoes")
    private String observacoes;

    @Column(name = "prazo_validacao_horas")
    private Integer prazoValidacaoHoras;

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
        return status.isAberto();
    }

    /**
     * UC "Criar anúncio de serviço" (ETI-59): grava o retrato do endereço no momento da criação e nasce
     * {@code PUBLICADO}, aguardando candidatos. Mudar o endereço salvo depois não muda o anúncio.
     */
    public void publicar(UUID contratanteId, UUID enderecoSalvoId, EnderecoLocalizado endereco,
            int prazoValidacaoHoras) {
        this.contratanteId = contratanteId;
        this.enderecoSalvoId = enderecoSalvoId;
        this.bairroRegiaoSnapshot = endereco.bairroRegiao();
        this.ufSnapshot = endereco.uf();
        this.enderecoCompletoSnapshot = endereco.enderecoCompleto();
        this.latitudeSnapshot = endereco.latitude();
        this.longitudeSnapshot = endereco.longitude();
        this.prazoValidacaoHoras = prazoValidacaoHoras;
        this.status = StatusAnuncio.PUBLICADO;
    }

    public boolean pertenceA(UUID usuarioId) {
        return contratanteId.equals(usuarioId);
    }

    /** UC "Anexar fotos ao anúncio": só enquanto está publicado e ninguém foi escolhido. */
    public boolean aceitaFotos(boolean temCandidaturaEscolhida) {
        return switch (status) {
            case PUBLICADO -> !temCandidaturaEscolhida;
            case EM_ANDAMENTO, AGUARDANDO_VALIDACAO, CONCLUIDO, ENCERRADO -> false;
        };
    }

    /** UC16 (encerrar conta do contratante): tira do mural, não conclui. */
    public void encerrarPorExclusaoDeConta(Instant agora) {
        this.status = StatusAnuncio.ENCERRADO;
        this.encerradoEm = agora;
    }

    /**
     * "Combinado a menos de {@code janela}": a data combinada ainda não
     * passou, mas está perto. Não depende de {@code status == EM_ANDAMENTO}
     * — esse status só é setado no check-in, que acontece depois de {@code
     * dataHoraPretendida}, então nunca coincidiria com "data ainda no
     * futuro" (TICKET-0049, achado de review). Quem chama já sabe que existe
     * candidatura escolhida/aguardando validação; aqui só a janela de tempo.
     */
    public boolean estaAMenosDe(Duration janela, Instant agora) {
        return agora.isBefore(dataHoraPretendida)
                && dataHoraPretendida.isBefore(agora.plus(janela));
    }
}
