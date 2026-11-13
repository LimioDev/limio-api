package com.limio.api.comum.persistencia.enums;

/**
 * Namespace de cada advisory lock do projeto ({@code TravaTransacional}). Lista central de propósito: dá
 * pra auditar aqui todos os locks em uso, e dois escopos diferentes nunca disputam a mesma chave.
 */
public enum EscopoTrava {

    /** Tentativas de login por e-mail (TICKET-0032). */
    LOGIN,

    /** Tentativas de login por IP. Sempre travado depois de {@link #LOGIN}, nunca antes (sem deadlock). */
    LOGIN_IP,

    /** Salvamento do perfil de atuação por usuário (TICKET-0054). */
    PERFIL_PRESTADOR,

    /** Troca da foto de perfil por usuário (TICKET-0056). */
    FOTO_PERFIL,

    /** Envio de fotos por anúncio, pro limite de quantidade (ETI-59). */
    FOTOS_ANUNCIO,

    /** Avaliações recebidas por usuário e papel, pro recálculo da média (ETI-107). */
    AVALIACOES_RECEBIDAS,

    /**
     * Mudança do conteúdo ou do estado de um anúncio e de suas candidaturas (ETI-61, ETI-69): editar, cancelar,
     * iniciar o serviço, candidatar-se, desistir, recusar, escolher candidato e validar o serviço (ETI-92). É o
     * mecanismo único do agregado: advisory lock não bloqueia linha nenhuma, só quem também o toma, por isso
     * todo escritor do anúncio ou das suas candidaturas trava este escopo como PRIMEIRA instrução da
     * transação, antes de ler o estado que vai decidir.
     *
     * <p>Ordem de aquisição quando um usecase precisa de mais de um recurso (sem deadlock): {@code ANUNCIO} →
     * linhas de {@code aplicacao} ({@code SELECT ... FOR UPDATE}, por id crescente) → {@link #COTA_PRESTADOR}.
     * {@link #FOTOS_ANUNCIO} também vem sempre depois de {@code ANUNCIO}.
     */
    ANUNCIO,

    /**
     * Cota mensal de escolhas de um Prestador, chave = id do Prestador (ETI-69). Protege o par ler-saldo/consumir
     * mesmo quando a linha do mês ainda não existe e quando o mesmo Prestador é escolhido em anúncios diferentes
     * ao mesmo tempo (o lock {@link #ANUNCIO} é por anúncio e não o cobre). Sempre depois de {@link #ANUNCIO}.
     */
    COTA_PRESTADOR,

    /** Assinar o plano GOLD por usuário (ETI-81): clique duplo não chama o gateway duas vezes. */
    ASSINATURA_GOLD
}
