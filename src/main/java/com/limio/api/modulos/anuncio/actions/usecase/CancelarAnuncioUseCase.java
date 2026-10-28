package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.AnuncioCanceladoEvent;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.excecao.AnuncioEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;

/**
 * UC — Empregador cancela um anúncio antes de escolher alguém (ETI-63): marca {@code ENCERRADO},
 * encerra as candidaturas ainda abertas e publica {@link AnuncioCanceladoEvent}, que o módulo {@code
 * chat} (ainda não implementado) escuta pra deixar os chats do anúncio em somente leitura.
 *
 * Dono errado e anúncio inexistente têm a mesma resposta (404 {@code ANUNCIO_NAO_ENCONTRADO}) — quem
 * não é dono não descobre se o id existe, mesmo critério de {@code ConsultarPainelEmpregadorUseCase}
 * (só lista os anúncios do próprio contratante).
 */
@Service
public class CancelarAnuncioUseCase {

    private static final String MOTIVO_CANDIDATURAS = "Anúncio cancelado pelo Empregador";

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ApplicationEventPublisher eventos;

    public CancelarAnuncioUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ApplicationEventPublisher eventos) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.eventos = eventos;
    }

    @Transactional
    public void executar(UUID anuncioId, UUID contratanteId) {
        Anuncio anuncio = anuncioService.buscarPorId(anuncioId)
                .filter(encontrado -> encontrado.getContratanteId().equals(contratanteId))
                .orElseThrow(AnuncioNaoEncontradoException::new);

        if (anuncio.getStatus() != StatusAnuncio.PUBLICADO || aplicacaoService.temCandidaturaEscolhida(anuncioId)) {
            throw new AnuncioEstadoInvalidoException();
        }

        Instant agora = Instant.now();

        // Só ENVIADA pode existir aqui: ESCOLHIDA/AGUARDANDO_VALIDACAO já teriam barrado acima.
        for (Aplicacao aplicacao : aplicacaoService.buscarAbertasDoAnuncio(anuncioId)) {
            aplicacao.encerrar(agora, MOTIVO_CANDIDATURAS);
            aplicacaoService.salvar(aplicacao);
        }

        anuncio.cancelarPeloEmpregador(agora);
        anuncioService.salvar(anuncio);

        eventos.publishEvent(new AnuncioCanceladoEvent(anuncioId, agora));
    }
}
