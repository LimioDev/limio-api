package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.ServicoIniciadoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/**
 * UC — Prestador escolhido inicia o serviço (ETI-88): o anúncio vai de {@code PUBLICADO} pra {@code
 * EM_ANDAMENTO}, a proposta de reagendamento pendente é encerrada sem efeito e {@link ServicoIniciadoEvent}
 * é publicado, que o módulo {@code notificacao} (ainda não implementado) escuta pra avisar o Empregador.
 *
 * A trava {@link EscopoTrava#ANUNCIO} (advisory lock, a mesma do resto do agregado) barra o clique duplo: a
 * segunda requisição espera a primeira, encontra {@code EM_ANDAMENTO} e recebe 409, sem publicar o evento de
 * novo. Ela também coloca o início em fila com cancelamento, recusa, desistência e escolha, coisa que o antigo
 * {@code SELECT ... FOR UPDATE} da linha do anúncio não fazia (os dois mecanismos não se bloqueiam).
 *
 * Serviço inexistente e quem não é o escolhido têm a mesma resposta (404 {@code SERVICO_NAO_ENCONTRADO}),
 * mesmo critério de {@code CancelarAnuncioUseCase}.
 */
@Service
public class IniciarServicoUseCase {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ReagendamentoService reagendamentoService;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;

    public IniciarServicoUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ReagendamentoService reagendamentoService, ApplicationEventPublisher eventos, TravaTransacional trava) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.reagendamentoService = reagendamentoService;
        this.eventos = eventos;
        this.trava = trava;
    }

    @Transactional
    public Anuncio executar(UUID anuncioId, UUID prestadorId) {
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Anuncio anuncio = anuncioService.buscarPorId(anuncioId)
                .orElseThrow(ServicoNaoEncontradoException::new);

        if (!aplicacaoService.foiEscolhido(anuncioId, prestadorId)) {
            throw new ServicoNaoEncontradoException();
        }

        if (!anuncio.permiteInicio()) {
            throw new ServicoEstadoInvalidoException();
        }

        Instant agora = Instant.now();

        for (Reagendamento reagendamento : reagendamentoService.buscarPendentesDosAnuncios(List.of(anuncioId))) {
            reagendamento.cancelarSemEfeito(agora);
            reagendamentoService.salvar(reagendamento);
        }

        anuncio.iniciar(agora);
        Anuncio salvo = anuncioService.salvar(anuncio);

        eventos.publishEvent(new ServicoIniciadoEvent(anuncioId, anuncio.getTitulo(), anuncio.getContratanteId(),
                prestadorId, agora));
        return salvo;
    }
}
