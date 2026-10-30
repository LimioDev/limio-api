package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.CandidaturaCriadaEvent;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.AnuncioEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.AnuncioProprioException;
import com.limio.api.modulos.anuncio.excecao.CandidaturaDuplicadaException;
import com.limio.api.modulos.anuncio.excecao.ContatosNaoConfirmadosException;
import com.limio.api.modulos.anuncio.excecao.CotaEsgotadaException;
import com.limio.api.modulos.anuncio.excecao.SomentePrestadorCandidataException;

/**
 * UC — Prestador se candidata a um anúncio (ETI-75): cria a {@link Aplicacao} em {@code ENVIADA} e
 * publica {@link CandidaturaCriadaEvent}, que o módulo {@code chat} (ainda não implementado) escuta pra
 * abrir o Chat 1:1 e o módulo {@code notificacao} (ainda não implementado, ETI-112) escuta pra avisar o
 * Empregador. Publicar antes dos consumidores existirem não quebra nada — evento sem listener.
 *
 * Ordem das checagens (nenhuma é pré-requisito técnico da outra, mas segue papel → conta → anúncio →
 * candidatura → cota, do mais genérico ao mais específico):
 * <ol>
 *   <li>papel ativo {@code PRESTADOR} (403 {@code ACESSO_NEGADO}, mesmo padrão de publicar anúncio);</li>
 *   <li>telefone e e-mail confirmados ({@link ConsultaDadosUsuario}, porta pra não importar {@code
 *       modulos.auth} — ADR-0001/0003);</li>
 *   <li>anúncio existe, não é do próprio Prestador, está {@code PUBLICADO} e sem candidatura
 *       escolhida;</li>
 *   <li>Prestador ainda não tem candidatura ativa pra este anúncio;</li>
 *   <li>cota mensal de escolhas gratuitas não esgotada.</li>
 * </ol>
 *
 * Não mexe na cota: candidatar-se é grátis, só a escolha consome (UC33, card futuro) — ver {@link
 * #cotaEsgotada}.
 */
@Service
public class CandidatarSeUseCase {

    private static final ZoneId FUSO_DA_COTA = ZoneId.of("America/Sao_Paulo");

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ApplicationEventPublisher eventos;
    private final int maxEscolhasGratuitas;

    public CandidatarSeUseCase(AnuncioService anuncioService,
            AplicacaoService aplicacaoService,
            ConsultaDadosUsuario consultaDadosUsuario,
            ApplicationEventPublisher eventos,
            @Value("${anuncio.candidatura.max-escolhas-gratuitas}") int maxEscolhasGratuitas) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.eventos = eventos;
        this.maxEscolhasGratuitas = maxEscolhasGratuitas;
    }

    @Transactional
    public Aplicacao executar(UUID anuncioId, UUID prestadorId, PapelUsuario papelAtivo) {
        exigirPrestador(papelAtivo);
        exigirContatosConfirmados(prestadorId);

        Anuncio anuncio = anuncioService.buscarPorId(anuncioId).orElseThrow(AnuncioNaoEncontradoException::new);
        if (anuncio.pertenceA(prestadorId)) {
            throw new AnuncioProprioException();
        }
        if (anuncio.getStatus() != StatusAnuncio.PUBLICADO || aplicacaoService.existeEscolhida(anuncioId)) {
            throw new AnuncioEstadoInvalidoException();
        }
        if (aplicacaoService.existeAbertaDoPar(anuncioId, prestadorId)) {
            throw new CandidaturaDuplicadaException();
        }

        Instant agora = Instant.now();
        if (cotaEsgotada(prestadorId, agora)) {
            throw new CotaEsgotadaException();
        }

        Aplicacao aplicacao = aplicacaoService.salvarCandidatura(Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(prestadorId)
                .status(StatusAplicacao.ENVIADA)
                .dataAplicacao(agora)
                .build());

        eventos.publishEvent(new CandidaturaCriadaEvent(aplicacao.getId(), anuncio.getId(), anuncio.getTitulo(),
                anuncio.getContratanteId(), prestadorId, agora));
        return aplicacao;
    }

    /** Mesmo padrão de {@code PublicarAnuncioUseCase}: papel errado responde 403 {@code ACESSO_NEGADO}. */
    private static void exigirPrestador(PapelUsuario papelAtivo) {
        switch (papelAtivo) {
            case PRESTADOR -> {
            }
            case EMPREGADOR -> throw new SomentePrestadorCandidataException();
        }
    }

    /**
     * Mesmo comportamento de {@code PublicarAnuncioUseCase}: conta sumida/encerrada entre o token ser emitido e
     * esta chamada também cai em {@code CONTATOS_NAO_CONFIRMADOS}.
     */
    private void exigirContatosConfirmados(UUID prestadorId) {
        boolean confirmados = consultaDadosUsuario.buscarPorId(prestadorId)
                .map(DadosUsuario::contatosConfirmados)
                .orElse(false);
        if (!confirmados) {
            throw new ContatosNaoConfirmadosException();
        }
    }

    /**
     * {@code financeiro} (plano/assinatura GOLD) ainda é só o esqueleto do ADR-0001 (TICKET-0044) — até
     * existir, todo Prestador é tratado como FREE, então o limite sempre vale. Mês civil de São Paulo: mesmo
     * critério da tela "Cota do mês" do Figma (reinicia no dia 1, 00:00).
     */
    private boolean cotaEsgotada(UUID prestadorId, Instant agora) {
        ZonedDateTime inicioDoMes = agora.atZone(FUSO_DA_COTA).toLocalDate().withDayOfMonth(1).atStartOfDay(FUSO_DA_COTA);
        Instant inicio = inicioDoMes.toInstant();
        Instant fimExclusivo = inicioDoMes.plusMonths(1).toInstant();
        return aplicacaoService.contarEscolhidasNoPeriodo(prestadorId, inicio, fimExclusivo) >= maxEscolhasGratuitas;
    }
}
