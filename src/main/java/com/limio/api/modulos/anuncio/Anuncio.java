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

    private static final String MOTIVO_EXCLUSAO_DE_CONTA = "Cancelado por saída de conta";
    private static final String MOTIVO_CANCELAMENTO_PELO_EMPREGADOR = "Cancelado pelo Empregador";

    @Column(name = "contratante_id", nullable = false)
    private UUID contratanteId;

    @Column(name = "anuncio_origem_id")
    private UUID anuncioOrigemId;

    @Column(name = "titulo", nullable = false, length = 100)
    private String titulo;

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

    /**
     * Por que o anúncio foi pra {@code ENCERRADO} — não existe status próprio pra "cancelado" nem
     * "expirado" (ETI-63: decisão pendente do card, resolvida assim). Texto livre, não enum: cada
     * caminho (exclusão de conta, cancelamento pelo Empregador, expiração futura — ETI-66) grava o
     * próprio motivo sem precisar de um valor novo em {@link StatusAnuncio} a cada card.
     */
    @Column(name = "motivo_encerramento")
    private String motivoEncerramento;

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

    /**
     * O Empregador ainda pode mexer no anúncio: só enquanto está publicado e ninguém foi escolhido. Regra única
     * de "Anexar fotos ao anúncio" (ETI-59) e "Cancelar anúncio antes da escolha" (ETI-63).
     */
    public boolean permiteAlteracao(boolean existeEscolhida) {
        return switch (status) {
            case PUBLICADO -> !existeEscolhida;
            case EM_ANDAMENTO, AGUARDANDO_VALIDACAO, CONCLUIDO, ENCERRADO -> false;
        };
    }

    /**
     * ETI-88: o serviço só começa a partir de {@code PUBLICADO}. Quem chama já confirmou que existe candidatura
     * escolhida. Passo opcional: validação presencial e check-out também partem de {@code PUBLICADO}.
     */
    public boolean permiteInicio() {
        return switch (status) {
            case PUBLICADO -> true;
            case EM_ANDAMENTO, AGUARDANDO_VALIDACAO, CONCLUIDO, ENCERRADO -> false;
        };
    }

    /** ETI-88: Prestador escolhido avisou que chegou. Quem chama já validou o estado do anúncio. */
    public void iniciar(Instant agora) {
        this.status = StatusAnuncio.EM_ANDAMENTO;
        this.iniciadoEm = agora;
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
     * — esse status só existe se o Prestador iniciar o serviço (ETI-88, passo
     * opcional), então um combinado próximo costuma seguir {@code PUBLICADO}
     * (TICKET-0049, achado de review). Quem chama já sabe que existe
     * candidatura escolhida/aguardando validação; aqui só a janela de tempo.
     */
    public boolean estaAMenosDe(Duration janela, Instant agora) {
        return agora.isBefore(dataHoraPretendida)
                && dataHoraPretendida.isBefore(agora.plus(janela));
    }
}
