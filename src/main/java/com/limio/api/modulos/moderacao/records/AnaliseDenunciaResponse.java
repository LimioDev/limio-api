package com.limio.api.modulos.moderacao.records;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;
import com.limio.api.modulos.moderacao.enums.Severidade;
import com.limio.api.modulos.moderacao.enums.StatusResolucao;
import com.limio.api.modulos.moderacao.enums.TipoAlvoDenuncia;

/**
 * Resposta de {@code GET /admin/denuncias/{id}} (ETI-116): a denúncia como o usuário preencheu no app, o alvo, as
 * evidências e o histórico do denunciado.
 *
 * @param evidencia        foto de comprovação, nula sem foto ou com o storage fora do ar
 * @param fotosCheckout    fotos do check-out do serviço do contexto
 * @param chat             texto original das mensagens, da mais antiga para a mais recente
 * @param outrasDenuncias  outras denúncias contra o mesmo denunciado
 * @param sancoesAnteriores sanções que o denunciado já recebeu em outras decisões
 * @param decisao          nula enquanto a denúncia está aberta
 */
public record AnaliseDenunciaResponse(
        UUID id,
        Long numero,
        MotivoDenuncia motivo,
        Severidade severidade,
        String descricao,
        StatusResolucao statusResolucao,
        Instant prazoAnalise,
        Instant criadaEm,
        Instant analisadaEm,
        ContaModeradaResponse denunciante,
        ContaModeradaResponse denunciado,
        Alvo alvo,
        LinkTemporario evidencia,
        List<LinkTemporario> fotosCheckout,
        List<Mensagem> chat,
        List<OutraDenuncia> outrasDenuncias,
        List<SancaoResponse> sancoesAnteriores,
        DecisaoModeracaoResponse decisao) {

    /** O que foi denunciado e o contexto gravado. {@code tituloAnuncio} nulo sem anúncio. */
    public record Alvo(TipoAlvoDenuncia tipo, UUID anuncioId, String tituloAnuncio, UUID candidaturaId,
            UUID mensagemId) {
    }

    /** {@code texto} é o que o remetente escreveu, sem máscara. */
    public record Mensagem(UUID id, UUID remetenteId, String texto, Instant enviadaEm) {
    }

    public record OutraDenuncia(UUID id, Long numero, MotivoDenuncia motivo, StatusResolucao statusResolucao,
            Instant criadaEm) {
    }
}
