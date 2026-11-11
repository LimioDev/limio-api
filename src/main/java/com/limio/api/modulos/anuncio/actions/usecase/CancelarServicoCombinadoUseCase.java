package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.eventos.ServicoCanceladoEvent;
import com.limio.api.comum.eventos.enums.TipoCancelamento;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.portas.ConsumoCotaPrestador;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.CancelamentoServico;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.excecao.PrazoCancelamentoEsgotadoException;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.UseDenunciaException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

import tools.jackson.databind.ObjectMapper;

/**
 * UC42 — Empregador cancela o serviço combinado, com 24h ou mais de antecedência (ETI-94, RF47/RB08/RB17/RB36).
 *
 * <p>"Combinado" é {@code PUBLICADO} com uma candidatura {@code ESCOLHIDA}. O cancelamento leva o anúncio a {@code
 * ENCERRADO} (com {@link TipoCancelamento}) e a candidatura a {@code ENCERRADA}, e encerra sem efeito a proposta de
 * reagendamento pendente. A antecedência é sempre contada da data/hora VIGENTE ({@code dataHoraPretendida}), nunca da
 * original.
 *
 * <ul>
 *   <li>{@code NORMAL}: estorna a escolha da cota FREE, se a escolha consumiu cota ({@code cotaConsumida}, gravado no
 *       instante da escolha — GOLD naquele momento não tem o que estornar, mesmo que o plano tenha mudado depois).
 *   <li>{@code TARDIO} (RB36): o último reagendamento aceito está dentro da carência. Permitido, sem estorno, com
 *       {@code LogAuditoria} da data anterior e da nova.
 * </ul>
 *
 * <p>O estorno é síncrono, pela porta {@link ConsumoCotaPrestador}, na MESMA transação do encerramento: falhou, tudo
 * desfaz; e uma segunda chamada nunca chega a ele, porque o anúncio já está {@code ENCERRADO}. O evento {@link
 * ServicoCanceladoEvent} só é consumido depois do commit (chat somente leitura; notificação, ETI-112).
 *
 * <p>Ordem de trava do agregado: advisory {@code ANUNCIO} (1ª instrução), linha da candidatura {@code FOR UPDATE},
 * depois {@code COTA_PRESTADOR} (dentro da porta). Serviço inexistente e quem não é o dono têm a mesma resposta (404).
 */
@Service
public class CancelarServicoCombinadoUseCase {

    static final String ORIGEM_AUDITORIA = "anuncio-cancelar-servico";

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ReagendamentoService reagendamentoService;
    private final ConsumoCotaPrestador consumoCotaPrestador;
    private final LogAuditoriaService logAuditoriaService;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;
    private final Clock relogio;
    private final ObjectMapper objectMapper;
    private final Duration carenciaPosReagendamento;

    public CancelarServicoCombinadoUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ReagendamentoService reagendamentoService, ConsumoCotaPrestador consumoCotaPrestador,
            LogAuditoriaService logAuditoriaService, ApplicationEventPublisher eventos, TravaTransacional trava,
            Clock relogio, ObjectMapper objectMapper,
            @Value("${anuncio.carencia-pos-reagendamento-horas}") long carenciaPosReagendamentoHoras) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.reagendamentoService = reagendamentoService;
        this.consumoCotaPrestador = consumoCotaPrestador;
        this.logAuditoriaService = logAuditoriaService;
        this.eventos = eventos;
        this.trava = trava;
        this.relogio = relogio;
        this.objectMapper = objectMapper;
        this.carenciaPosReagendamento = Duration.ofHours(carenciaPosReagendamentoHoras);
    }

    /** @param ip endereço de quem pediu, para a auditoria do cancelamento tardio */
    @Transactional
    public CancelamentoServico executar(UUID contratanteId, UUID anuncioId, String ip) {
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Anuncio anuncio = anuncioService.buscarPorId(anuncioId)
                .filter(encontrado -> encontrado.pertenceA(contratanteId))
                .orElseThrow(ServicoNaoEncontradoException::new);
        exigirCombinado(anuncio);
        Aplicacao escolhida = aplicacaoService.buscarEscolhidaDoAnuncioParaAtualizar(anuncioId)
                .orElseThrow(ServicoEstadoInvalidoException::new);

        Instant agora = relogio.instant();
        if (!anuncio.temAntecedenciaDe(ConsultarServicoUseCase.PRAZO_CANCELAMENTO, agora)) {
            throw new PrazoCancelamentoEsgotadoException();
        }

        Optional<Reagendamento> naCarencia = reagendamentoService.buscarUltimoAceito(anuncioId)
                .filter(aceito -> aceito.getRespondidoEm() != null
                        && aceito.getRespondidoEm().plus(carenciaPosReagendamento).isAfter(agora));
        TipoCancelamento tipo = naCarencia.isPresent() ? TipoCancelamento.TARDIO : TipoCancelamento.NORMAL;
        Instant dataHoraVigente = anuncio.getDataHoraPretendida();

        for (Reagendamento pendente : reagendamentoService.buscarPendentesDosAnuncios(List.of(anuncioId))) {
            pendente.cancelarSemEfeito(agora);
            reagendamentoService.salvar(pendente);
        }

        anuncio.cancelarServicoCombinado(agora, tipo);
        Anuncio salvo = anuncioService.salvar(anuncio);

        escolhida.encerrarPorCancelamentoDoServico(agora);
        boolean cotaEstornada = tipo == TipoCancelamento.NORMAL && escolhida.isCotaConsumida()
                && consumoCotaPrestador.estornarEscolha(escolhida.getPrestadorId(), escolhida.getEscolhidaEm());
        if (cotaEstornada) {
            escolhida.registrarEstornoDeCota(agora);
        }
        aplicacaoService.salvar(escolhida);

        naCarencia.ifPresent(reagendamento -> registrarAuditoriaDoCancelamentoTardio(anuncioId, reagendamento, ip));

        eventos.publishEvent(new ServicoCanceladoEvent(anuncioId, anuncio.getTitulo(), escolhida.getId(),
                contratanteId, escolhida.getPrestadorId(), tipo, cotaEstornada, dataHoraVigente, agora));
        return new CancelamentoServico(salvo, cotaEstornada);
    }

    /**
     * Só o serviço combinado e ainda não começado se cancela por aqui. Em andamento ou aguardando validação o caminho
     * é a denúncia; concluído ou encerrado (inclui o cancelamento repetido) não tem mais o que cancelar. {@code switch}
     * sem {@code default}: um status novo obriga a decidir aqui.
     */
    private static void exigirCombinado(Anuncio anuncio) {
        switch (anuncio.getStatus()) {
            case PUBLICADO -> {
            }
            case EM_ANDAMENTO, AGUARDANDO_VALIDACAO -> throw new UseDenunciaException();
            case CONCLUIDO, ENCERRADO -> throw new ServicoEstadoInvalidoException();
        }
    }

    private void registrarAuditoriaDoCancelamentoTardio(UUID anuncioId, Reagendamento aceito, String ip) {
        Map<String, String> detalhes = new LinkedHashMap<>();
        detalhes.put("dataAnterior", aceito.getDataAnterior().toString());
        detalhes.put("dataNova", aceito.getDataProposta().toString());
        detalhes.put("aceitoEm", aceito.getRespondidoEm().toString());
        logAuditoriaService.registrar("Anuncio", anuncioId, TipoAcaoAuditoria.COMBINADO_CANCELADO_TARDIO,
                ORIGEM_AUDITORIA, ip, objectMapper.writeValueAsString(detalhes));
    }
}
