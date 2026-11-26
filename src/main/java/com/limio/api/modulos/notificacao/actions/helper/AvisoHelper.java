package com.limio.api.modulos.notificacao.actions.helper;

import java.util.Locale;
import java.util.UUID;

import com.limio.api.comum.eventos.enums.OrigemConclusao;
import com.limio.api.modulos.notificacao.Aviso;
import com.limio.api.modulos.notificacao.enums.TipoNotificacao;

/**
 * Textos dos avisos (ETI-112). Os marcados no card seguem a tela 43; os "a definir" do card são proposta desta entrega,
 * fáceis de trocar aqui sem mexer em mais nada. Puro: recebe tudo pronto, inclusive o nome já abreviado.
 */
public final class AvisoHelper {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final int TAMANHO_TITULO = 120;

    private AvisoHelper() {
    }

    /** ETI-65: "Novo serviço na sua área — (categoria) em (bairro), a N km de distância." */
    public static Aviso novoServicoNaArea(UUID prestadorId, UUID anuncioId, String categoria, String bairro,
            double distanciaKm) {
        return new Aviso(prestadorId, TipoNotificacao.NOVO_SERVICO_NA_AREA, "Novo serviço na sua área",
                categoria + " em " + bairro + ", a " + String.format(PT_BR, "%.1f", distanciaKm)
                        + " km de distância.",
                anuncioId);
    }

    /** ETI-75: "Novo candidato — (nome) se candidatou a '(anúncio)'." */
    public static Aviso novaCandidatura(UUID empregadorId, UUID anuncioId, String nomePrestador, String anuncio) {
        return new Aviso(empregadorId, TipoNotificacao.NOVA_CANDIDATURA, "Novo candidato",
                nomePrestador + " se candidatou a '" + anuncio + "'.", anuncioId);
    }

    /** ETI-67 (texto a definir no card). */
    public static Aviso candidaturaRecusada(UUID prestadorId, UUID anuncioId, String anuncio) {
        return new Aviso(prestadorId, TipoNotificacao.CANDIDATURA_RECUSADA, "Candidatura não aceita",
                "Sua candidatura a '" + anuncio + "' não foi aceita desta vez. Veja outros serviços no mural.",
                anuncioId);
    }

    /** ETI-69, pro escolhido (texto a definir no card). */
    public static Aviso prestadorEscolhido(UUID prestadorId, UUID anuncioId, String anuncio) {
        return new Aviso(prestadorId, TipoNotificacao.PRESTADOR_ESCOLHIDO, "Você foi escolhido",
                "Você foi escolhido para '" + anuncio + "'. Os contatos já estão liberados no serviço combinado.",
                anuncioId);
    }

    /** ETI-69, pros não escolhidos (texto a definir no card). */
    public static Aviso naoEscolhido(UUID prestadorId, UUID anuncioId, String anuncio) {
        return new Aviso(prestadorId, TipoNotificacao.CANDIDATURA_ENCERRADA, "Vaga preenchida",
                "O anúncio '" + anuncio + "' escolheu outro prestador. Veja outros serviços no mural.", anuncioId);
    }

    /** ETI-88 (texto a definir no card). */
    public static Aviso servicoIniciado(UUID empregadorId, UUID anuncioId, String nomePrestador, String anuncio) {
        return new Aviso(empregadorId, TipoNotificacao.SERVICO_INICIADO, "Serviço iniciado",
                nomePrestador + " fez o check-in e começou '" + anuncio + "'.", anuncioId);
    }

    /** ETI-90: "Check-out enviado — (nome) enviou a foto de '(anúncio)'. Valide para finalizar o serviço." */
    public static Aviso checkoutEnviado(UUID empregadorId, UUID anuncioId, String nomePrestador, String anuncio) {
        return new Aviso(empregadorId, TipoNotificacao.CHECKOUT_ENVIADO, "Check-out enviado",
                nomePrestador + " enviou a foto de '" + anuncio + "'. Valide para finalizar o serviço.", anuncioId);
    }

    /** ETI-92 e ETI-98, pras duas partes (texto a definir no card). */
    public static Aviso servicoConcluido(UUID destinatarioId, UUID anuncioId, String anuncio, OrigemConclusao origem) {
        String conclusao = switch (origem) {
            case VALIDACAO -> "foi concluído";
            case SILENCIO -> "foi concluído automaticamente";
        };
        return new Aviso(destinatarioId, TipoNotificacao.SERVICO_CONCLUIDO, "Serviço concluído",
                "'" + anuncio + "' " + conclusao + ". Avalie a outra parte.", anuncioId);
    }

    /** TICKET-0049: aviso genérico de anúncio (hoje, só o encerramento por exclusão de conta). */
    public static Aviso avisoDeAnuncio(UUID destinatarioId, UUID anuncioId, String assunto) {
        String titulo = assunto.length() <= TAMANHO_TITULO ? assunto : assunto.substring(0, TAMANHO_TITULO);
        return new Aviso(destinatarioId, TipoNotificacao.AVISO_DE_ANUNCIO, titulo,
                "Toque para ver os detalhes do anúncio.", anuncioId);
    }
}
