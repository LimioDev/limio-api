package com.limio.api.modulos.financeiro.actions.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGoldService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.Cobranca;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.CobrancaService;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal.DocumentoFiscal;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal.DocumentoFiscalService;
import com.limio.api.modulos.financeiro.actions.service.GatewayPagamento;
import com.limio.api.modulos.financeiro.actions.service.ResultadoCobranca;
import com.limio.api.modulos.financeiro.eventos.GoldAssinadoEvent;
import com.limio.api.modulos.financeiro.excecao.AssinaturaJaAtivaException;
import com.limio.api.modulos.financeiro.excecao.PagamentoRecusadoException;
import com.limio.api.modulos.financeiro.excecao.PlanoNaoEncontradoException;
import com.limio.api.modulos.financeiro.excecao.SomentePrestadorAssinaGoldException;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamentoService;
import com.limio.api.modulos.financeiro.plano.Plano;
import com.limio.api.modulos.financeiro.plano.PlanoService;

/**
 * UC — Prestador assina o plano GOLD (ETI-81): cobra no gateway e, se aprovado, grava
 * {@code AssinaturaGold} + {@code Cobranca} + {@code DocumentoFiscal} e publica
 * {@link GoldAssinadoEvent} pro recibo. Recusado: nada é persistido, o Prestador permanece FREE.
 *
 * <p>Clique duplo: {@link #travaTransacional} serializa as chamadas do mesmo usuário até o fim da
 * transação (mesmo padrão de {@code PerfilPrestadorService.travarPorUsuario}) — a segunda chamada só
 * roda depois que a primeira comita, encontra a assinatura já {@code ATIVA} e é recusada aqui mesmo,
 * sem chegar a chamar o gateway de novo. {@code chaveIdempotencia} (índice único em {@code cobranca})
 * é a rede de segurança caso a trava falhe sob corrida real.
 */
@Service
public class AssinarGoldUseCase {

    private final PlanoService planoService;
    private final AssinaturaGoldService assinaturaGoldService;
    private final MeioPagamentoService meioPagamentoService;
    private final CobrancaService cobrancaService;
    private final DocumentoFiscalService documentoFiscalService;
    private final GatewayPagamento gatewayPagamento;
    private final TravaTransacional travaTransacional;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public AssinarGoldUseCase(PlanoService planoService,
            AssinaturaGoldService assinaturaGoldService,
            MeioPagamentoService meioPagamentoService,
            CobrancaService cobrancaService,
            DocumentoFiscalService documentoFiscalService,
            GatewayPagamento gatewayPagamento,
            TravaTransacional travaTransacional,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.planoService = planoService;
        this.assinaturaGoldService = assinaturaGoldService;
        this.meioPagamentoService = meioPagamentoService;
        this.cobrancaService = cobrancaService;
        this.documentoFiscalService = documentoFiscalService;
        this.gatewayPagamento = gatewayPagamento;
        this.travaTransacional = travaTransacional;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @Transactional
    public AssinaturaGold executar(UUID usuarioId, PapelUsuario papelAtivo, String codigoPlano, String gatewayToken) {
        exigirPrestador(papelAtivo);
        travaTransacional.travar(EscopoTrava.ASSINATURA_GOLD, usuarioId.toString());

        Plano plano = planoService.buscarAtivoPorCodigo(codigoPlano).orElseThrow(PlanoNaoEncontradoException::new);
        Optional<AssinaturaGold> existente = assinaturaGoldService.buscarPorUsuario(usuarioId);
        if (existente.isPresent() && existente.get().estaAtiva()) {
            throw new AssinaturaJaAtivaException();
        }

        String chaveIdempotencia = UUID.randomUUID().toString();
        ResultadoCobranca resultado = gatewayPagamento.cobrar(usuarioId, gatewayToken, plano.getPrecoCentavos(),
                plano.getMoeda(), chaveIdempotencia);
        if (!resultado.aprovado()) {
            throw new PagamentoRecusadoException();
        }

        meioPagamentoService.salvarOuSubstituir(usuarioId, gatewayToken, resultado.bandeira(), resultado.ultimos4());

        Instant inicio = relogio.instant();
        Instant fim = proximoCiclo(inicio, plano);
        AssinaturaGold assinatura = existente
                .map(a -> {
                    a.reativar(plano.getId(), inicio, fim);
                    return a;
                })
                .orElseGet(() -> AssinaturaGold.nova(usuarioId, plano.getId(), inicio, fim));
        assinatura = assinaturaGoldService.salvar(assinatura);

        Cobranca cobranca = cobrancaService.salvar(
                Cobranca.paga(assinatura.getId(), plano.getPrecoCentavos(), resultado.gatewayCobrancaId(), chaveIdempotencia));
        DocumentoFiscal recibo = documentoFiscalService.salvar(DocumentoFiscal.emitir(cobranca, inicio));

        eventos.publishEvent(new GoldAssinadoEvent(usuarioId, plano.getNome(), plano.getPrecoCentavos(), plano.getMoeda(),
                fim, recibo.getNumero()));
        return assinatura;
    }

    private static Instant proximoCiclo(Instant inicio, Plano plano) {
        return switch (plano.getCiclo()) {
            case MENSAL -> inicio.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        };
    }

    private static void exigirPrestador(PapelUsuario papelAtivo) {
        switch (papelAtivo) {
            case PRESTADOR -> {
            }
            case EMPREGADOR -> throw new SomentePrestadorAssinaGoldException();
        }
    }
}
