package com.limio.api.comum.portas;

import java.time.Instant;
import java.util.UUID;

/**
 * Porta de ESCRITA da cota mensal do Prestador FREE, para módulos que não podem importar {@code modulos.perfil}
 * (hoje o {@code anuncio}, na escolha do candidato — ETI-69). Quem implementa é o {@code perfil}, dono de {@code
 * cota_mensal}.
 *
 * <p>Diferente das demais portas de {@code comum/portas}, que só consultam, esta muda estado, e de propósito: o
 * consumo precisa estar na MESMA transação da escolha (a cota só sobe se a candidatura virar {@code ESCOLHIDA}, e
 * volta junto num rollback), o que um evento {@code AFTER_COMMIT} não garante. Por isso exige transação aberta do
 * chamador.
 */
public interface ConsumoCotaPrestador {

    /**
     * Consome uma escolha da cota do mês de {@code ocorridaEm} (mês civil de {@code America/Sao_Paulo}), se houver
     * saldo. Serializa por Prestador: o par "ler saldo / consumir" não corre contra outra escolha do mesmo Prestador,
     * nem em anúncios diferentes. Cria a linha do mês se ainda não existir.
     *
     * <p>Quem chama decide se precisa consumir (GOLD ativo não consome, e isso é consulta de plano, não daqui).
     *
     * @param prestadorId id do usuário Prestador
     * @return {@code true} se consumiu; {@code false} se a cota do mês já estava esgotada (nada é alterado)
     */
    boolean consumirEscolha(UUID prestadorId, Instant ocorridaEm);

    /**
     * Devolve uma escolha à cota do mês em que ela foi consumida — o mês de {@code escolhidaEm}, não o atual (RB17:
     * "a escolha daquele mês"). Mesma serialização por Prestador do consumo, e mesma exigência de transação do
     * chamador: o estorno confirma ou desfaz junto com o cancelamento do serviço (ETI-94).
     *
     * <p>Quem chama decide se há o que estornar (só escolha que consumiu cota FREE, e só no cancelamento normal), e
     * garante que não chama duas vezes pela mesma escolha — esta porta não guarda histórico. Piso em zero.
     *
     * @param prestadorId id do usuário Prestador
     * @param escolhidaEm instante em que a escolha consumiu a cota
     * @return {@code true} se decrementou; {@code false} se não havia o que devolver (mês sem linha ou já zerado;
     *         nada é alterado)
     */
    boolean estornarEscolha(UUID prestadorId, Instant escolhidaEm);
}
