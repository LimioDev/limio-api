package com.limio.api.modulos.financeiro.assinaturagold;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.financeiro.assinaturagold.enums.StatusAssinatura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Assinatura do Prestador ao plano GOLD (ETI-81). {@code usuarioId} é coluna crua, sem FK pra {@code
 * Usuario} de {@code modulos.auth} (ADR-0001 §1.6). No máximo uma por usuário: reassinar depois de
 * cancelada ({@link StatusAssinatura#CANCELADA}, card futuro US26/US27) reativa esta mesma linha em vez
 * de criar outra, pra manter o GOLD sem interrupção quando o ciclo ainda está vigente.
 *
 * <p>Nasce/reinicia {@link StatusAssinatura#PENDENTE} numa transação curta, antes de chamar o gateway
 * (ver {@code AssinarGoldUseCase}): o índice único em {@code usuario_id} é a trava contra uma segunda
 * tentativa concorrente enquanto a primeira ainda não confirmou. {@link #confirmarAtiva()} e
 * {@link #cancelarPendencia()} resolvem a pendência numa segunda transação curta, fora da chamada ao
 * gateway.
 *
 * <p>Renovação (ETI-85): passado {@code dataFimCiclo}, a rotina {@code RenovarGoldUseCase} cobra o próximo ciclo.
 * Aprovada, {@link #renovar} avança o ciclo. Recusada, {@link #agendarNovaTentativa} guarda quando tentar de novo e a
 * assinatura continua {@code ATIVA} (o Prestador segue GOLD). Esgotadas as tentativas, ou com a renovação cancelada
 * ({@code renovacaoCanceladaEm}), {@link #encerrar} volta o Prestador para FREE.
 */
@Entity
@Table(name = "assinatura_gold")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssinaturaGold extends BaseEntity {

    @Column(name = "usuario_id", nullable = false, unique = true, updatable = false)
    private UUID usuarioId;

    @Column(name = "plano_id", nullable = false)
    private UUID planoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private StatusAssinatura status;

    @Column(name = "data_inicio_ciclo", nullable = false)
    private Instant dataInicioCiclo;

    @Column(name = "data_fim_ciclo", nullable = false)
    private Instant dataFimCiclo;

    /** Quando a próxima cobrança da renovação pode sair. Nula = assim que {@code dataFimCiclo} passar. */
    @Column(name = "proxima_tentativa_em")
    private Instant proximaTentativaEm;

    /**
     * O Prestador cancelou a renovação (US27). Quem grava é o card "Gerenciar a assinatura GOLD"; aqui só se lê, para
     * encerrar no fim do ciclo em vez de cobrar.
     */
    @Column(name = "renovacao_cancelada_em")
    private Instant renovacaoCanceladaEm;

    @Column(name = "encerrada_em")
    private Instant encerradaEm;

    private AssinaturaGold(UUID usuarioId, UUID planoId, Instant dataInicioCiclo, Instant dataFimCiclo) {
        this.usuarioId = usuarioId;
        this.planoId = planoId;
        this.status = StatusAssinatura.PENDENTE;
        this.dataInicioCiclo = dataInicioCiclo;
        this.dataFimCiclo = dataFimCiclo;
    }

    public static AssinaturaGold nova(UUID usuarioId, UUID planoId, Instant dataInicioCiclo, Instant dataFimCiclo) {
        return new AssinaturaGold(usuarioId, planoId, dataInicioCiclo, dataFimCiclo);
    }

    /** Reinicia a cobrança numa linha {@code CANCELADA} existente em vez de criar outra para o usuário. */
    public void reiniciarPendencia(UUID planoId, Instant dataInicioCiclo, Instant dataFimCiclo) {
        this.planoId = planoId;
        this.status = StatusAssinatura.PENDENTE;
        this.dataInicioCiclo = dataInicioCiclo;
        this.dataFimCiclo = dataFimCiclo;
        this.proximaTentativaEm = null;
        this.renovacaoCanceladaEm = null;
        this.encerradaEm = null;
    }

    /** Pagamento aprovado: a pendência criada por {@link #nova} ou {@link #reiniciarPendencia} confirma. */
    public void confirmarAtiva() {
        this.status = StatusAssinatura.ATIVA;
    }

    /** Pagamento recusado: a pendência não vira assinatura — a linha fica como se nunca tivesse sido ativada. */
    public void cancelarPendencia() {
        this.status = StatusAssinatura.CANCELADA;
    }

    public boolean estaAtiva() {
        return status == StatusAssinatura.ATIVA;
    }

    public boolean estaPendente() {
        return status == StatusAssinatura.PENDENTE;
    }

    /** Ativa, ciclo vencido e sem nova tentativa marcada para depois de {@code agora}. */
    public boolean renovacaoVencida(Instant agora) {
        return estaAtiva() && !dataFimCiclo.isAfter(agora)
                && (proximaTentativaEm == null || !proximaTentativaEm.isAfter(agora));
    }

    public boolean renovacaoCancelada() {
        return renovacaoCanceladaEm != null;
    }

    /**
     * Uma cobrança de renovação só mexe na assinatura se ainda for a do ciclo em aberto: ativa e com {@code
     * dataFimCiclo} igual ao início que a cobrança paga. Um aviso atrasado de um ciclo já renovado ou encerrado não
     * avança nem encerra nada.
     */
    public boolean aguardaRenovacaoDe(Instant inicioCicloCobrado) {
        return estaAtiva() && dataFimCiclo.equals(inicioCicloCobrado);
    }

    /** Renovação aprovada: o ciclo novo começa onde o anterior terminou, mesmo que a aprovação tenha vindo depois. */
    public void renovar(Instant novoFimCiclo) {
        this.dataInicioCiclo = this.dataFimCiclo;
        this.dataFimCiclo = novoFimCiclo;
        this.proximaTentativaEm = null;
    }

    public void agendarNovaTentativa(Instant quando) {
        this.proximaTentativaEm = quando;
    }

    /** Volta para FREE: tentativas esgotadas ou fim do ciclo com a renovação cancelada. */
    public void encerrar(Instant agora) {
        this.status = StatusAssinatura.ENCERRADA;
        this.encerradaEm = agora;
        this.proximaTentativaEm = null;
    }
}
