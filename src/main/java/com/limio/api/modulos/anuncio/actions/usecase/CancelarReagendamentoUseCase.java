package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.eventos.ReagendamentoCanceladoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.excecao.PropostaEncerradaException;
import com.limio.api.modulos.anuncio.excecao.PropostaExpiradaException;
import com.limio.api.modulos.anuncio.excecao.ReagendamentoNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.SomenteProponenteCancelaException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/**
 * O proponente cancela a própria proposta de reagendamento, antes da resposta (ETI-99, US46). A proposta fica {@code
 * CANCELADO} e a data vigente não muda; sem proposta pendente, a conclusão por silêncio e as demais regras voltam
 * ao normal.
 *
 * <p>Ordem das validações: a proposta existe e o usuário é parte do serviço (senão 404), é o proponente (senão 403),
 * está pendente (senão 409 {@code PROPOSTA_ENCERRADA}) e ainda dentro do prazo (senão 409 {@code PROPOSTA_EXPIRADA}: a
 * que passou do prazo é encerrada pelo job de expiração, não por cancelamento, e assim o registro fica fiel).
 *
 * <p>Trava: o {@code anuncioId} sai de uma consulta escalar, a trava advisory {@code ANUNCIO} é tomada, e só então a
 * proposta é lida — serializa com aceitar, recusar, expirar e cancelar o serviço, que usam a mesma trava.
 */
@Service
public class CancelarReagendamentoUseCase {

    private static final String ORIGEM_AUDITORIA = "anuncio-reagendamento-cancelar";

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ReagendamentoService reagendamentoService;
    private final LogAuditoriaService logAuditoriaService;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;
    private final Clock relogio;

    public CancelarReagendamentoUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ReagendamentoService reagendamentoService, LogAuditoriaService logAuditoriaService,
            ApplicationEventPublisher eventos, TravaTransacional trava, Clock relogio) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.reagendamentoService = reagendamentoService;
        this.logAuditoriaService = logAuditoriaService;
        this.eventos = eventos;
        this.trava = trava;
        this.relogio = relogio;
    }

    @Transactional
    public Reagendamento executar(UUID usuarioId, UUID reagendamentoId, String ip) {
        UUID anuncioId = reagendamentoService.buscarAnuncioId(reagendamentoId)
                .orElseThrow(ReagendamentoNaoEncontradoException::new);
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Reagendamento proposta = reagendamentoService.buscarPorId(reagendamentoId)
                .orElseThrow(ReagendamentoNaoEncontradoException::new);
        Anuncio anuncio = anuncioService.buscarPorId(anuncioId).orElseThrow(ReagendamentoNaoEncontradoException::new);
        UUID prestadorId = aplicacaoService.buscarPrestadorEscolhido(anuncioId).orElse(null);

        if (!anuncio.pertenceA(usuarioId) && !usuarioId.equals(prestadorId)) {
            throw new ReagendamentoNaoEncontradoException();
        }
        if (!proposta.foiProposta(usuarioId)) {
            throw new SomenteProponenteCancelaException();
        }
        if (!proposta.estaPendente()) {
            throw new PropostaEncerradaException();
        }
        Instant agora = relogio.instant();
        if (proposta.estaVencida(agora)) {
            throw new PropostaExpiradaException();
        }

        proposta.cancelarPeloProponente(agora);
        Reagendamento cancelada = reagendamentoService.salvar(proposta);

        registrarAuditoria(cancelada, ip);
        eventos.publishEvent(new ReagendamentoCanceladoEvent(cancelada.getId(), anuncioId, anuncio.getTitulo(),
                anuncio.getContratanteId(), prestadorId, usuarioId, cancelada.getDataAnterior(),
                cancelada.getDataProposta(), agora));
        return cancelada;
    }

    private void registrarAuditoria(Reagendamento cancelada, String ip) {
        Map<String, String> detalhes = new LinkedHashMap<>();
        detalhes.put("anuncioId", cancelada.getAnuncioId().toString());
        detalhes.put("proponenteId", cancelada.getProponenteId().toString());
        detalhes.put("dataAnterior", cancelada.getDataAnterior().toString());
        detalhes.put("dataProposta", cancelada.getDataProposta().toString());
        logAuditoriaService.registrar("Reagendamento", cancelada.getId(), TipoAcaoAuditoria.REAGENDAMENTO_CANCELADO,
                ORIGEM_AUDITORIA, ip, detalhes);
    }
}
