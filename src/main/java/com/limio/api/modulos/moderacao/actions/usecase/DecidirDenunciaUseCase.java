package com.limio.api.modulos.moderacao.actions.usecase;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.eventos.AvaliacaoOcultadaEvent;
import com.limio.api.comum.eventos.ContaSuspensaEvent;
import com.limio.api.comum.eventos.EncerramentoDeContaDecididoEvent;
import com.limio.api.comum.eventos.FotoPerfilRemovidaPelaModeracaoEvent;
import com.limio.api.modulos.moderacao.DenunciaDecidida;
import com.limio.api.modulos.moderacao.PedidoDeDecisao;
import com.limio.api.modulos.moderacao.avaliacao.Avaliacao;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoService;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacoesRecebidas;
import com.limio.api.modulos.moderacao.denuncia.DecisaoModeracao;
import com.limio.api.modulos.moderacao.denuncia.DecisaoModeracaoService;
import com.limio.api.modulos.moderacao.denuncia.Denuncia;
import com.limio.api.modulos.moderacao.denuncia.DenunciaService;
import com.limio.api.modulos.moderacao.denuncia.Sancao;
import com.limio.api.modulos.moderacao.enums.TipoSancao;
import com.limio.api.modulos.moderacao.excecao.DecisaoInvalidaException;
import com.limio.api.modulos.moderacao.excecao.DenunciaJaResolvidaException;
import com.limio.api.modulos.moderacao.excecao.DenunciaNaoEncontradaException;

/**
 * Decidir denúncia (ETI-116). Numa transação só: grava a {@link DecisaoModeracao}, tira a denúncia da fila, aplica a
 * sanção, remove a foto de perfil quando pedido e grava o {@code LogAuditoria}.
 *
 * <p>O que é de outro módulo vai por evento (ADR-0003): a suspensão ({@link ContaSuspensaEvent}), o encerramento de
 * conta ({@link EncerramentoDeContaDecididoEvent}), a foto ({@link FotoPerfilRemovidaPelaModeracaoEvent}) e a média
 * sem a avaliação oculta ({@link AvaliacaoOcultadaEvent}). Os ouvintes de {@code auth} e {@code perfil} rodam na mesma
 * transação: se um falhar, a decisão desfaz. Os anúncios encerram depois do commit.
 *
 * <p>Dinheiro não passa por aqui (UC): o administrador nunca marca o serviço como pago e não transfere valores.
 */
@Component
public class DecidirDenunciaUseCase {

    private static final String ORIGEM = "moderacao-decidir-denuncia";

    private final DenunciaService denunciaService;
    private final DecisaoModeracaoService decisaoModeracaoService;
    private final AvaliacaoService avaliacaoService;
    private final LogAuditoriaService logAuditoriaService;
    private final ApplicationEventPublisher eventos;

    public DecidirDenunciaUseCase(DenunciaService denunciaService, DecisaoModeracaoService decisaoModeracaoService,
            AvaliacaoService avaliacaoService, LogAuditoriaService logAuditoriaService,
            ApplicationEventPublisher eventos) {
        this.denunciaService = denunciaService;
        this.decisaoModeracaoService = decisaoModeracaoService;
        this.avaliacaoService = avaliacaoService;
        this.logAuditoriaService = logAuditoriaService;
        this.eventos = eventos;
    }

    @Transactional
    public DenunciaDecidida executar(UUID denunciaId, UUID administradorId, PedidoDeDecisao pedido, String ip) {
        Instant agora = Instant.now();
        Denuncia denuncia = denunciaService.buscarParaDecidir(denunciaId)
                .orElseThrow(DenunciaNaoEncontradaException::new);
        if (!denuncia.isAberta()) {
            throw new DenunciaJaResolvidaException();
        }
        validar(pedido, agora);
        Avaliacao avaliacao = pedido.sancao() == TipoSancao.OCULTAR_AVALIACAO
                ? avaliacaoDoDenunciado(pedido.avaliacaoId(), denuncia)
                : null;

        DecisaoModeracao decisao = decisaoModeracaoService.salvarAgora(DecisaoModeracao.registrar(denunciaId,
                pedido.veredito(), pedido.fundamento(), administradorId, agora));
        denuncia.resolver(pedido.veredito(), agora);
        denunciaService.salvar(denuncia);

        List<Sancao> sancoes = pedido.sancao() == null
                ? List.of()
                : List.of(aplicar(decisao, denuncia.getDenunciadoId(), pedido, avaliacao, administradorId, agora));
        if (pedido.removerFotoPerfil()) {
            eventos.publishEvent(new FotoPerfilRemovidaPelaModeracaoEvent(denuncia.getDenunciadoId(), agora));
        }

        logAuditoriaService.registrar("Denuncia", denunciaId, TipoAcaoAuditoria.DENUNCIA_DECIDIDA, ORIGEM, ip,
                detalhes(denuncia, decisao, pedido));
        return new DenunciaDecidida(denuncia, decisao, sancoes);
    }

    private static void validar(PedidoDeDecisao pedido, Instant agora) {
        switch (pedido.veredito()) {
            case IMPROCEDENTE -> {
                exigir(pedido.sancao() == null, "Decisão improcedente não tem sanção.");
                exigir(!pedido.removerFotoPerfil(), "Remover a foto de perfil só numa decisão procedente.");
            }
            case PROCEDENTE -> exigir(pedido.sancao() != null, "Escolha a sanção da decisão procedente.");
        }
        boolean suspensao = pedido.sancao() == TipoSancao.SUSPENSAO;
        boolean temPeriodo = pedido.inicioEm() != null || pedido.fimEm() != null;
        exigir(suspensao || !temPeriodo, "Início e fim só valem para a suspensão.");
        if (suspensao) {
            exigir(pedido.inicioEm() != null && pedido.fimEm() != null, "A suspensão precisa de início e fim.");
            exigir(pedido.fimEm().isAfter(pedido.inicioEm()), "O fim da suspensão precisa ser depois do início.");
            exigir(pedido.fimEm().isAfter(agora), "O fim da suspensão precisa estar no futuro.");
        }
        boolean ocultar = pedido.sancao() == TipoSancao.OCULTAR_AVALIACAO;
        exigir(ocultar || pedido.avaliacaoId() == null, "A avaliação só vale para ocultar avaliação.");
        exigir(!ocultar || pedido.avaliacaoId() != null, "Informe a avaliação a ocultar.");
    }

    /** Só uma avaliação escrita pelo denunciado e ainda visível. Inexistente responde igual a de outra pessoa. */
    private Avaliacao avaliacaoDoDenunciado(UUID avaliacaoId, Denuncia denuncia) {
        Avaliacao avaliacao = avaliacaoService.buscarPorId(avaliacaoId)
                .filter(encontrada -> encontrada.getAvaliadorId().equals(denuncia.getDenunciadoId()))
                .orElseThrow(() -> new DecisaoInvalidaException(
                        "A avaliação a ocultar precisa ser uma das escritas pelo denunciado."));
        exigir(!avaliacao.isOculta(), "Esta avaliação já está oculta.");
        return avaliacao;
    }

    private Sancao aplicar(DecisaoModeracao decisao, UUID denunciadoId, PedidoDeDecisao pedido, Avaliacao avaliacao,
            UUID administradorId, Instant agora) {
        switch (pedido.sancao()) {
            case ADVERTENCIA -> {
                // só o registro da sanção
            }
            case OCULTAR_AVALIACAO -> ocultar(avaliacao, administradorId, agora);
            case SUSPENSAO -> eventos.publishEvent(new ContaSuspensaEvent(denunciadoId, pedido.fimEm(), agora));
            case ENCERRAMENTO_DE_CONTA ->
                eventos.publishEvent(new EncerramentoDeContaDecididoEvent(denunciadoId, agora));
        }
        return decisaoModeracaoService.salvarSancao(Sancao.aplicar(decisao, denunciadoId, pedido.sancao(),
                pedido.inicioEm(), pedido.fimEm(), pedido.avaliacaoId()));
    }

    /** Mesma trava e mesmo recálculo da publicação (ETI-107): a média no perfil sai sem a avaliação oculta. */
    private void ocultar(Avaliacao avaliacao, UUID administradorId, Instant agora) {
        avaliacaoService.travarRecebidas(avaliacao.getAvaliadoId(), avaliacao.getPapelAvaliado());
        avaliacao.ocultar(agora, administradorId);
        avaliacaoService.salvar(avaliacao);
        AvaliacoesRecebidas total = avaliacaoService.recalcular(avaliacao.getAvaliadoId(),
                avaliacao.getPapelAvaliado());
        eventos.publishEvent(new AvaliacaoOcultadaEvent(avaliacao.getAvaliadoId(), avaliacao.getPapelAvaliado(),
                total.media(), total.qtd(), agora));
    }

    private static Map<String, Object> detalhes(Denuncia denuncia, DecisaoModeracao decisao, PedidoDeDecisao pedido) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("administradorId", decisao.getAdministradorId());
        detalhes.put("denunciadoId", denuncia.getDenunciadoId());
        detalhes.put("veredito", pedido.veredito());
        detalhes.put("sancao", pedido.sancao());
        detalhes.put("removerFotoPerfil", pedido.removerFotoPerfil());
        return detalhes;
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new DecisaoInvalidaException(mensagem);
        }
    }
}
