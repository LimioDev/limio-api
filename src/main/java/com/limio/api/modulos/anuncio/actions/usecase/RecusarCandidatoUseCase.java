package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.CandidaturaRecusadaEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.AnuncioEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.CandidaturaEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.CandidaturaNaoEncontradaException;

/**
 * Recusar um candidato pela lista (ETI-67). Só a candidatura {@code ENVIADA} muda: vira {@code RECUSADA} com
 * {@code recusadaEm}. O anúncio continua {@code PUBLICADO} e as outras candidaturas ficam como estão — recusar
 * nunca escolhe ninguém, nem quando era o único candidato.
 *
 * O anúncio é travado com {@link EscopoTrava#ANUNCIO} até o fim da transação — a mesma trava de editar e de
 * anexar/remover foto, e a que a escolha de um candidato vai tomar: duas recusas simultâneas da mesma candidatura,
 * uma recusa durante uma edição ou durante uma escolha ficam em fila, e a segunda vê o estado já mudado. É
 * advisory lock, não {@code SELECT ... FOR UPDATE}: os dois não se bloqueiam no Postgres, então o agregado usa um
 * mecanismo só. O evento é publicado dentro da transação; quem reage a ele roda só depois do commit (ADR-0003).
 */
@Service
public class RecusarCandidatoUseCase {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;

    public RecusarCandidatoUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ApplicationEventPublisher eventos, TravaTransacional trava) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.eventos = eventos;
        this.trava = trava;
    }

    @Transactional
    public void executar(UUID contratanteId, UUID anuncioId, UUID candidaturaId) {
        // O lock vem antes de ler o anúncio e a candidatura. Dono errado e anúncio inexistente respondem igual (404).
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Anuncio anuncio = anuncioService.buscarPorId(anuncioId)
                .filter(encontrado -> encontrado.pertenceA(contratanteId))
                .orElseThrow(AnuncioNaoEncontradoException::new);
        if (anuncio.getStatus() != StatusAnuncio.PUBLICADO) {
            throw new AnuncioEstadoInvalidoException();
        }

        Aplicacao aplicacao = aplicacaoService.buscarPorId(candidaturaId)
                .filter(encontrada -> encontrada.getAnuncioId().equals(anuncioId))
                .orElseThrow(CandidaturaNaoEncontradaException::new);
        if (aplicacao.getStatus() != StatusAplicacao.ENVIADA) {
            throw new CandidaturaEstadoInvalidoException();
        }

        Instant agora = Instant.now();
        aplicacao.recusar(agora);
        aplicacaoService.salvar(aplicacao);

        eventos.publishEvent(new CandidaturaRecusadaEvent(aplicacao.getId(), anuncioId, anuncio.getTitulo(),
                contratanteId, aplicacao.getPrestadorId(), agora));
    }
}
