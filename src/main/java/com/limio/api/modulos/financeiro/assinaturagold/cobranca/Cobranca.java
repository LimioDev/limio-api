package com.limio.api.modulos.financeiro.assinaturagold.cobranca;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums.StatusCobranca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Cobrança de uma {@link AssinaturaGold} no gateway (ETI-81), subentidade sem ciclo de vida próprio.
 * Nasce {@link StatusCobranca#PENDENTE} numa transação curta, antes da chamada ao gateway, e
 * {@link #confirmarAprovada} ou {@link #confirmarRecusada} resolvem numa segunda transação curta, fora
 * dela (ver {@code AssinarGoldUseCase}). {@code chaveIdempotencia} tem índice único no banco — rede de
 * segurança contra cobrar duas vezes se a trava transacional do usecase falhar sob corrida real (mesmo
 * padrão de {@code uk_aplicacao_par_ativo}).
 *
 * <p>Renovação (ETI-85): {@link #renovacao} guarda o início do ciclo que a cobrança paga ({@code inicioCicloCobrado})
 * e o número da tentativa no ciclo. O aviso do gateway resolve uma cobrança ainda sem resultado ({@link
 * #aguardaResultado}); uma cobrança já resolvida não muda mais, e é isso que torna o aviso repetido inofensivo.
 */
@Entity
@Table(name = "cobranca")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cobranca extends BaseEntity {

    @Column(name = "assinatura_gold_id", nullable = false, updatable = false)
    private UUID assinaturaGoldId;

    @Column(name = "valor_centavos", nullable = false, updatable = false)
    private long valorCentavos;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private StatusCobranca status;

    @Column(name = "gateway_cobranca_id")
    private String gatewayCobrancaId;

    @Column(name = "chave_idempotencia", nullable = false, updatable = false, length = 100)
    private String chaveIdempotencia;

    /** Início do ciclo que esta cobrança de renovação paga. Nulo na cobrança da assinatura (ETI-81). */
    @Column(name = "inicio_ciclo_cobrado", updatable = false)
    private Instant inicioCicloCobrado;

    @Column(name = "tentativa", nullable = false, updatable = false)
    private int tentativa;

    private Cobranca(UUID assinaturaGoldId, long valorCentavos, String chaveIdempotencia, Instant inicioCicloCobrado,
            int tentativa) {
        this.assinaturaGoldId = assinaturaGoldId;
        this.valorCentavos = valorCentavos;
        this.status = StatusCobranca.PENDENTE;
        this.chaveIdempotencia = chaveIdempotencia;
        this.inicioCicloCobrado = inicioCicloCobrado;
        this.tentativa = tentativa;
    }

    /** Cobrança da assinatura (ETI-81). */
    public static Cobranca pendente(UUID assinaturaGoldId, long valorCentavos, String chaveIdempotencia) {
        return new Cobranca(assinaturaGoldId, valorCentavos, chaveIdempotencia, null, 1);
    }

    /** Cobrança da renovação do ciclo que começa em {@code inicioCicloCobrado} (ETI-85). */
    public static Cobranca renovacao(UUID assinaturaGoldId, long valorCentavos, String chaveIdempotencia,
            Instant inicioCicloCobrado, int tentativa) {
        return new Cobranca(assinaturaGoldId, valorCentavos, chaveIdempotencia, inicioCicloCobrado, tentativa);
    }

    /** Gateway aprovou: resolve a pendência criada por {@link #pendente}. */
    public void confirmarAprovada(String gatewayCobrancaId) {
        this.status = StatusCobranca.APROVADA;
        this.gatewayCobrancaId = gatewayCobrancaId;
    }

    /** Gateway recusou: resolve a pendência sem emitir recibo nem ativar a assinatura. */
    public void confirmarRecusada() {
        this.status = StatusCobranca.RECUSADA;
    }

    /** O gateway não respondeu (ETI-85): sem resultado conhecido, a renovação tenta de novo depois. */
    public void marcarFalha() {
        this.status = StatusCobranca.FALHOU;
    }

    /** Ainda sem resultado do gateway: o aviso dele pode resolver. */
    public boolean aguardaResultado() {
        return status == StatusCobranca.PENDENTE || status == StatusCobranca.FALHOU;
    }

    public boolean ehRenovacao() {
        return inicioCicloCobrado != null;
    }
}
