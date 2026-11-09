package com.limio.api.modulos.moderacao.actions.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.AvaliacaoPublicadaEvent;
import com.limio.api.comum.portas.ConsultaServico;
import com.limio.api.comum.portas.ResumoServico;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.moderacao.actions.helper.JanelaAvaliacaoHelper;
import com.limio.api.modulos.moderacao.avaliacao.Avaliacao;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoService;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacoesRecebidas;
import com.limio.api.modulos.moderacao.excecao.AvaliacaoDuplicadaException;
import com.limio.api.modulos.moderacao.excecao.ComentarioSemNotaException;
import com.limio.api.modulos.moderacao.excecao.JanelaAvaliacaoEncerradaException;
import com.limio.api.modulos.moderacao.excecao.NotaObrigatoriaException;
import com.limio.api.modulos.moderacao.excecao.ServicoNaoConcluidoException;
import com.limio.api.modulos.moderacao.excecao.ServicoNaoEncontradoException;

/**
 * UC "Avaliar a outra parte" (ETI-107). Só as duas partes, só com o serviço concluído, dentro de 7 dias e uma vez por
 * serviço. Grava a avaliação, recalcula o total do avaliado no papel que ele teve no serviço e publica o
 * {@link AvaliacaoPublicadaEvent}, que leva a média ao perfil na mesma transação. Não avaliar não bloqueia nada.
 */
@Service
public class AvaliarUseCase {

    private final ConsultaServico consultaServico;
    private final AvaliacaoService avaliacaoService;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public AvaliarUseCase(ConsultaServico consultaServico, AvaliacaoService avaliacaoService,
            ApplicationEventPublisher eventos, Clock relogio) {
        this.consultaServico = consultaServico;
        this.avaliacaoService = avaliacaoService;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /**
     * @param nota de 1 a 5 (a faixa já vem validada pelo record); sem nota é recusado
     * @param comentario opcional; em branco conta como ausente
     */
    @Transactional
    public Avaliacao executar(UUID anuncioId, UUID avaliadorId, Integer nota, String comentario) {
        String comentarioInformado = comentario == null || comentario.isBlank() ? null : comentario.strip();
        if (nota == null) {
            throw comentarioInformado == null ? new NotaObrigatoriaException() : new ComentarioSemNotaException();
        }

        ResumoServico servico = consultaServico.buscarPorId(anuncioId)
                .filter(encontrado -> encontrado.participa(avaliadorId))
                .orElseThrow(ServicoNaoEncontradoException::new);
        if (!servico.concluido()) {
            throw new ServicoNaoConcluidoException();
        }
        Instant agora = Instant.now(relogio);
        if (!JanelaAvaliacaoHelper.aberta(servico.concluidoEm(), agora)) {
            throw new JanelaAvaliacaoEncerradaException();
        }

        UUID avaliadoId = servico.outraParte(avaliadorId);
        PapelUsuario papelAvaliado = servico.papelDe(avaliadoId);
        // A mesma pessoa avaliando duas vezes cai na mesma trava (mesmo avaliado e papel): a checagem abaixo já vê a
        // primeira, e o índice único fica de rede de segurança.
        avaliacaoService.travarRecebidas(avaliadoId, papelAvaliado);
        if (avaliacaoService.jaAvaliou(anuncioId, avaliadorId)) {
            throw new AvaliacaoDuplicadaException();
        }

        Avaliacao avaliacao = avaliacaoService.salvarNova(Avaliacao.publicar(anuncioId, avaliadorId, avaliadoId,
                papelAvaliado, nota, comentarioInformado, agora));
        AvaliacoesRecebidas total = avaliacaoService.recalcular(avaliadoId, papelAvaliado);
        eventos.publishEvent(new AvaliacaoPublicadaEvent(avaliadoId, papelAvaliado, total.media(), total.qtd(), agora));
        return avaliacao;
    }
}
