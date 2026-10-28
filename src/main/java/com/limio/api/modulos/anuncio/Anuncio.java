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

    @Column(name = "titulo", nullable = false, length = 100)
    private String titulo;

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

    /**
     * Por que o anúncio foi pra {@code ENCERRADO} — não existe status próprio pra "cancelado" nem
     * "expirado" (ETI-63: decisão pendente do card, resolvida assim). Texto livre, não enum: cada
     * caminho (exclusão de conta, cancelamento pelo Empregador, expiração futura — ETI-66) grava o
     * próprio motivo sem precisar de um valor novo em {@link StatusAnuncio} a cada card.
     */
    @Column(name = "motivo_encerramento")
    private String motivoEncerramento;

    private static final String MOTIVO_EXCLUSAO_DE_CONTA = "Cancelado por saída de conta";
    private static final String MOTIVO_CANCELAMENTO_PELO_EMPREGADOR = "Cancelado pelo Empregador";

    /** Status ainda "vivo" — ainda não chegou num estado final (TICKET-0049: candidato a encerramento). */
    public boolean isAberto() {
        return status.isAberto();
    }

    /** UC16 (encerrar conta do contratante): tira do mural, não conclui. */
    public void encerrarPorExclusaoDeConta(Instant agora) {
        encerrar(agora, MOTIVO_EXCLUSAO_DE_CONTA);
    }

    /** ETI-63: Empregador cancela antes de escolher alguém. Quem chama já validou o estado do anúncio. */
    public void cancelarPeloEmpregador(Instant agora) {
        encerrar(agora, MOTIVO_CANCELAMENTO_PELO_EMPREGADOR);
    }

    private void encerrar(Instant agora, String motivo) {
        this.status = StatusAnuncio.ENCERRADO;
        this.encerradoEm = agora;
        this.motivoEncerramento = motivo;
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
