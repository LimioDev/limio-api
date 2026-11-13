package com.limio.api.comum.portas;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

/**
 * Porta de leitura das avaliações recebidas por um usuário para módulos que não podem importar
 * {@code modulos.moderacao}, dono da entity {@code Avaliacao} (hoje os candidatos do anúncio — ETI-67 — e o
 * perfil público — ETI-105). Só consulta.
 */
public interface ConsultaAvaliacoes {

    /**
     * Resumo (quantidade + média) de quem foi avaliado como Prestador, em lote pra não consultar um Prestador
     * por vez.
     *
     * @return resumo por id de usuário; quem nunca foi avaliado pode não aparecer no mapa
     */
    Map<UUID, ResumoAvaliacoes> resumir(Collection<UUID> prestadorIds);

    /**
     * As avaliações recebidas mais recentes de um usuário num papel, pro card de reputação do perfil público
     * (ETI-105). {@code avaliadorNome} já abreviado.
     *
     * <p>Avaliação ocultada pela moderação não deveria entrar na lista, mas a entity {@code Avaliacao} ainda
     * não tem esse campo (denúncia/moderação de conteúdo, {@code modulos.moderacao}, continua esqueleto) —
     * hoje nada é filtrado por esse motivo.
     *
     * @param avaliadoId id de quem recebeu as avaliações
     * @param papel papel que {@code avaliadoId} teve no serviço avaliado
     * @param limite quantas trazer, da mais recente pra mais antiga
     * @return lista vazia se o usuário nunca foi avaliado nesse papel
     */
    List<AvaliacaoRecente> listarRecentes(UUID avaliadoId, PapelUsuario papel, int limite);
}
