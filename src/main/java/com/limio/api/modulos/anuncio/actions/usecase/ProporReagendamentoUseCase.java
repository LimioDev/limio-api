package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.eventos.ReagendamentoPropostoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.SituacaoCancelamentoServico;
import com.limio.api.modulos.anuncio.excecao.DataIgualAVigenteException;
import com.limio.api.modulos.anuncio.excecao.DataNoPassadoException;
import com.limio.api.modulos.anuncio.excecao.LimiteReagendamentosException;
import com.limio.api.modulos.anuncio.excecao.PropostaPendenteExistenteException;
import com.limio.api.modulos.anuncio.excecao.ReagendamentoNaoPermitidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/**
 * Propor nova data e hora pro serviço combinado (ETI-99, US46). Tanto o Empregador quanto o Prestador escolhido podem
 * propor; a contraparte responde depois (aceitar/recusar) e o proponente pode cancelar antes disso.
 *
 * <p>"Combinado" é {@code PUBLICADO} com a candidatura {@code ESCOLHIDA}, a mesma regra do cancelamento (ETI-94). A
 * proposta não muda o anúncio: a data vigente ({@code dataHoraPretendida}) só troca no aceite. Ordem das validações:
 * serviço combinado, data futura, data diferente da vigente (aceitar uma igual gastaria um dos reagendamentos e
 * abriria a carência sem mudar nada), nenhuma proposta pendente, limite de reagendamentos ACEITOS (recusadas, canceladas e
 * expiradas não contam) e, se a data vigente já passou, a única proposta permitida nessa situação. A proposta expira no
 * menor entre {@code agora + prazo de resposta} e a nova data: depois dela o aceite não seria mais possível.
 *
 * <p>Ordem de trava do agregado: advisory {@code ANUNCIO} (1ª instrução) e a linha da candidatura {@code FOR UPDATE}.
 * Quem não é parte do serviço recebe 404, igual a um serviço inexistente. O índice único parcial de proposta
 * pendente (V40) é só a rede de segurança do banco: sob a trava, a checagem de pendente já serializa as propostas.
 */
@Service
public class ProporReagendamentoUseCase {

    private static final String ORIGEM_AUDITORIA = "anuncio-reagendamento-propor";

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ReagendamentoService reagendamentoService;
    private final LogAuditoriaService logAuditoriaService;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;
    private final Clock relogio;
    private final int limiteReagendamentos;
    private final Duration prazoResposta;

    public ProporReagendamentoUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ReagendamentoService reagendamentoService, LogAuditoriaService logAuditoriaService,
            ApplicationEventPublisher eventos, TravaTransacional trava, Clock relogio,
            @Value("${anuncio.reagendamento.limite}") int limiteReagendamentos,
            @Value("${anuncio.reagendamento.prazo-resposta-horas}") long prazoRespostaHoras) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.reagendamentoService = reagendamentoService;
        this.logAuditoriaService = logAuditoriaService;
        this.eventos = eventos;
        this.trava = trava;
        this.relogio = relogio;
        this.limiteReagendamentos = limiteReagendamentos;
        this.prazoResposta = Duration.ofHours(prazoRespostaHoras);
    }

    @Transactional
    public Reagendamento executar(UUID usuarioId, UUID anuncioId, Instant novaDataHora, String justificativa,
            String ip) {
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Anuncio anuncio = anuncioService.buscarPorId(anuncioId)
                .filter(encontrado -> ehParteDoServico(encontrado, usuarioId))
                .orElseThrow(ServicoNaoEncontradoException::new);
        if (anuncio.situacaoParaCancelarServico() != SituacaoCancelamentoServico.CANCELAVEL) {
            throw new ServicoEstadoInvalidoException();
        }
        Aplicacao escolhida = aplicacaoService.buscarEscolhidaDoAnuncioParaAtualizar(anuncioId)
                .orElseThrow(ServicoEstadoInvalidoException::new);

        Instant agora = relogio.instant();
        if (!novaDataHora.isAfter(agora)) {
            throw new DataNoPassadoException();
        }
        Instant dataVigente = anuncio.getDataHoraPretendida();
        if (novaDataHora.equals(dataVigente)) {
            throw new DataIgualAVigenteException();
        }
        if (reagendamentoService.existePendente(anuncioId)) {
            throw new PropostaPendenteExistenteException();
        }
        if (reagendamentoService.contarAceitos(anuncioId) >= limiteReagendamentos) {
            throw new LimiteReagendamentosException();
        }
        boolean aposDataVigente = !agora.isBefore(dataVigente);
        if (aposDataVigente && reagendamentoService.existePropostaAposDataVigente(anuncioId)) {
            throw new ReagendamentoNaoPermitidoException();
        }

        Reagendamento proposta = reagendamentoService.salvar(Reagendamento.propor(anuncioId, usuarioId, dataVigente,
                novaDataHora, justificativa.strip(), prazoDaProposta(agora, novaDataHora), aposDataVigente));

        registrarAuditoria(proposta, ip);
        eventos.publishEvent(new ReagendamentoPropostoEvent(proposta.getId(), anuncioId, anuncio.getTitulo(),
                anuncio.getContratanteId(), escolhida.getPrestadorId(), usuarioId, dataVigente, novaDataHora,
                proposta.getExpiraEm(), agora));
        return proposta;
    }

    /**
     * O prazo de resposta da contraparte, limitado à nova data: depois dela o aceite daria 422, então a proposta não
     * pode seguir pendente (bloqueando nova proposta e a conclusão por silêncio) além desse instante.
     */
    private Instant prazoDaProposta(Instant agora, Instant novaDataHora) {
        Instant fimDoPrazo = agora.plus(prazoResposta);
        return fimDoPrazo.isBefore(novaDataHora) ? fimDoPrazo : novaDataHora;
    }

    /**
     * O Empregador do anúncio ou o Prestador escolhido, inclusive depois do check-out ou do cancelamento do serviço:
     * esses casos caem em 409 (estado) adiante, não em 404.
     */
    private boolean ehParteDoServico(Anuncio anuncio, UUID usuarioId) {
        return anuncio.pertenceA(usuarioId)
                || aplicacaoService.buscarPrestadorEscolhido(anuncio.getId()).filter(usuarioId::equals).isPresent();
    }

    private void registrarAuditoria(Reagendamento proposta, String ip) {
        Map<String, String> detalhes = new LinkedHashMap<>();
        detalhes.put("anuncioId", proposta.getAnuncioId().toString());
        detalhes.put("proponenteId", proposta.getProponenteId().toString());
        detalhes.put("dataAnterior", proposta.getDataAnterior().toString());
        detalhes.put("dataProposta", proposta.getDataProposta().toString());
        detalhes.put("expiraEm", proposta.getExpiraEm().toString());
        logAuditoriaService.registrar("Reagendamento", proposta.getId(), TipoAcaoAuditoria.REAGENDAMENTO_PROPOSTO,
                ORIGEM_AUDITORIA, ip, detalhes);
    }
}
