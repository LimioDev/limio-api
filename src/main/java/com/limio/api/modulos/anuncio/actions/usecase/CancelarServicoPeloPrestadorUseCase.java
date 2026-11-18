package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.ServicoCanceladoPeloPrestadorEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/**
 * UC43 — Prestador escolhido cancela o serviço combinado (ETI-96, RF48/RB18).
 *
 * <p>Vale com o anúncio {@code PUBLICADO} (combinado) ou {@code EM_ANDAMENTO}, e a candidatura {@code ESCOLHIDA}. Em
 * {@code AGUARDANDO_VALIDACAO} recusa: seguem a validação, o silêncio ou a denúncia. Leva o anúncio a {@code
 * ENCERRADO} (terminal: não volta ao mural nem aceita candidatura) e a candidatura a {@code ENCERRADA}, e encerra sem
 * efeito a proposta de reagendamento pendente, tudo na mesma transação.
 *
 * <p>NÃO mexe na cota: a escolha já consumida continua consumida (RB18), e a regra de cancelamento tardio (RB36) não
 * se aplica ao Prestador. Por isso este caso de uso não conhece {@code ConsumoCotaPrestador}.
 *
 * <p>Só o Prestador da candidatura escolhida cancela; quem não é ele recebe o mesmo 404 de serviço inexistente. A
 * autorização vem ANTES da checagem de estado para que um terceiro não descubra em que estado o serviço está, e usa
 * {@code escolhidaEm} (permanente), não o status atual: o escolhido que repete o pedido recebe 409.
 *
 * <p>Ordem de trava do agregado: advisory {@code ANUNCIO} (1ª instrução), depois a linha da candidatura {@code FOR
 * UPDATE}. O evento só é consumido depois do commit (chat somente leitura; aviso ao Empregador, US56/ETI-112).
 */
@Service
public class CancelarServicoPeloPrestadorUseCase {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ReagendamentoService reagendamentoService;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;
    private final Clock relogio;

    public CancelarServicoPeloPrestadorUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ReagendamentoService reagendamentoService, ApplicationEventPublisher eventos, TravaTransacional trava,
            Clock relogio) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.reagendamentoService = reagendamentoService;
        this.eventos = eventos;
        this.trava = trava;
        this.relogio = relogio;
    }

    @Transactional
    public Anuncio executar(UUID prestadorId, UUID anuncioId) {
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Anuncio anuncio = anuncioService.buscarPorId(anuncioId).orElseThrow(ServicoNaoEncontradoException::new);
        // escolhidaEm é permanente: quem foi o escolhido continua sendo, e um segundo pedido cai em 409, não em 404
        aplicacaoService.buscarEscolhidaDoAnuncio(anuncioId)
                .filter(escolhidaDoServico -> escolhidaDoServico.getPrestadorId().equals(prestadorId))
                .orElseThrow(ServicoNaoEncontradoException::new);
        if (!anuncio.servicoEmAberto()) {
            throw new ServicoEstadoInvalidoException();
        }
        Aplicacao escolhida = aplicacaoService.buscarEscolhidaDoAnuncioParaAtualizar(anuncioId)
                .orElseThrow(ServicoEstadoInvalidoException::new);

        Instant agora = relogio.instant();
        for (Reagendamento pendente : reagendamentoService.buscarPendentesDosAnuncios(List.of(anuncioId))) {
            pendente.cancelarSemEfeito(agora);
            reagendamentoService.salvar(pendente);
        }

        anuncio.cancelarServicoPeloPrestador(agora);
        Anuncio salvo = anuncioService.salvar(anuncio);
        escolhida.encerrarPorCancelamentoDoServicoPeloPrestador(agora);
        aplicacaoService.salvar(escolhida);

        eventos.publishEvent(new ServicoCanceladoPeloPrestadorEvent(anuncioId, anuncio.getTitulo(), escolhida.getId(),
                anuncio.getContratanteId(), prestadorId, agora));
        return salvo;
    }
}
