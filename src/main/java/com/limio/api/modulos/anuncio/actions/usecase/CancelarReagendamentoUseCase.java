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
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.ReagendamentoConsultado;
import com.limio.api.modulos.anuncio.actions.helper.PropostaSobTravaHelper;
import com.limio.api.modulos.anuncio.actions.helper.PropostaSobTravaHelper.PropostaCarregada;
import com.limio.api.modulos.anuncio.excecao.PropostaEncerradaException;
import com.limio.api.modulos.anuncio.excecao.PropostaExpiradaException;
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
 * <p>Trava: {@link PropostaSobTravaHelper} toma a trava advisory {@code ANUNCIO} antes de ler a proposta — serializa
 * com aceitar, recusar, expirar e cancelar o serviço, que usam a mesma trava.
 */
@Service
public class CancelarReagendamentoUseCase {

    private static final String ORIGEM_AUDITORIA = "anuncio-reagendamento-cancelar";

    private final ReagendamentoService reagendamentoService;
    private final LogAuditoriaService logAuditoriaService;
    private final ApplicationEventPublisher eventos;
    private final PropostaSobTravaHelper propostaSobTrava;
    private final Clock relogio;

    public CancelarReagendamentoUseCase(ReagendamentoService reagendamentoService,
            LogAuditoriaService logAuditoriaService, ApplicationEventPublisher eventos,
            PropostaSobTravaHelper propostaSobTrava, Clock relogio) {
        this.reagendamentoService = reagendamentoService;
        this.logAuditoriaService = logAuditoriaService;
        this.eventos = eventos;
        this.propostaSobTrava = propostaSobTrava;
        this.relogio = relogio;
    }

    @Transactional
    public ReagendamentoConsultado executar(UUID usuarioId, UUID reagendamentoId, String ip) {
        PropostaCarregada carregada = propostaSobTrava.carregarParaParte(usuarioId, reagendamentoId);
        Reagendamento proposta = carregada.proposta();
        Anuncio anuncio = carregada.anuncio();
        UUID anuncioId = anuncio.getId();
        UUID prestadorId = carregada.prestadorId();

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
        return new ReagendamentoConsultado(cancelada, anuncio.getDataHoraPretendida());
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
