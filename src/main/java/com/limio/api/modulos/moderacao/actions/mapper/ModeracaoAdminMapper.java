package com.limio.api.modulos.moderacao.actions.mapper;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.ContaParaModeracao;
import com.limio.api.comum.records.PageResponse;
import com.limio.api.modulos.moderacao.AnaliseDaDenuncia;
import com.limio.api.modulos.moderacao.DenunciaDecidida;
import com.limio.api.modulos.moderacao.ItemDaFila;
import com.limio.api.modulos.moderacao.PedidoDeDecisao;
import com.limio.api.modulos.moderacao.denuncia.DecisaoModeracao;
import com.limio.api.modulos.moderacao.denuncia.Denuncia;
import com.limio.api.modulos.moderacao.denuncia.Sancao;
import com.limio.api.modulos.moderacao.enums.Severidade;
import com.limio.api.modulos.moderacao.records.AnaliseDenunciaResponse;
import com.limio.api.modulos.moderacao.records.ContaModeradaResponse;
import com.limio.api.modulos.moderacao.records.DecidirDenunciaRequest;
import com.limio.api.modulos.moderacao.records.DecisaoModeracaoResponse;
import com.limio.api.modulos.moderacao.records.DenunciaDecididaResponse;
import com.limio.api.modulos.moderacao.records.FilaDenunciaItemResponse;
import com.limio.api.modulos.moderacao.records.SancaoResponse;

/** Fila, análise e decisão de denúncias do administrador (ETI-116): domínio ↔ contrato HTTP. */
@Component
public class ModeracaoAdminMapper {

    public PageResponse<FilaDenunciaItemResponse> toResponse(Page<ItemDaFila> fila) {
        return PageResponse.de(fila, this::toItem);
    }

    public PedidoDeDecisao toPedido(DecidirDenunciaRequest request) {
        return new PedidoDeDecisao(request.veredito(), request.fundamento(), request.sancao(), request.inicioEm(),
                request.fimEm(), request.avaliacaoId(), Boolean.TRUE.equals(request.removerFotoPerfil()));
    }

    public AnaliseDenunciaResponse toResponse(AnaliseDaDenuncia analise) {
        Denuncia denuncia = analise.denuncia();
        return new AnaliseDenunciaResponse(
                denuncia.getId(),
                denuncia.getNumero(),
                denuncia.getMotivo(),
                Severidade.de(denuncia.getMotivo()),
                denuncia.getDescricao(),
                denuncia.getStatusResolucao(),
                denuncia.getPrazoAnalise(),
                denuncia.getCriadoEm(),
                denuncia.getAnalisadaEm(),
                toConta(analise.denunciante()),
                toConta(analise.denunciado()),
                new AnaliseDenunciaResponse.Alvo(analise.tipoAlvo(), denuncia.getAnuncioId(), analise.tituloAnuncio(),
                        denuncia.getCandidaturaId(), denuncia.getMensagemId()),
                analise.evidencia(),
                analise.fotosCheckout(),
                analise.chat().stream()
                        .map(mensagem -> new AnaliseDenunciaResponse.Mensagem(mensagem.id(), mensagem.remetenteId(),
                                mensagem.texto(), mensagem.enviadaEm()))
                        .toList(),
                analise.outrasDenuncias().stream()
                        .map(outra -> new AnaliseDenunciaResponse.OutraDenuncia(outra.getId(), outra.getNumero(),
                                outra.getMotivo(), outra.getStatusResolucao(), outra.getCriadoEm()))
                        .toList(),
                toSancoes(analise.sancoesAnteriores()),
                analise.decisao() == null ? null : toDecisao(analise.decisao(), analise.sancoesDaDecisao()));
    }

    public DenunciaDecididaResponse toResponse(DenunciaDecidida decidida) {
        Denuncia denuncia = decidida.denuncia();
        return new DenunciaDecididaResponse(denuncia.getId(), denuncia.getNumero(), denuncia.getStatusResolucao(),
                denuncia.getAnalisadaEm(), toDecisao(decidida.decisao(), decidida.sancoes()));
    }

    private FilaDenunciaItemResponse toItem(ItemDaFila item) {
        Denuncia denuncia = item.denuncia();
        return new FilaDenunciaItemResponse(denuncia.getId(), denuncia.getNumero(), denuncia.getMotivo(),
                Severidade.de(denuncia.getMotivo()), denuncia.getDenunciadoId(),
                item.denunciado() == null ? null : item.denunciado().nome(), denuncia.getPrazoAnalise(),
                denuncia.getCriadoEm());
    }

    private static ContaModeradaResponse toConta(ContaParaModeracao conta) {
        return conta == null ? null
                : new ContaModeradaResponse(conta.usuarioId(), conta.nome(), conta.papel(), conta.situacao());
    }

    private static DecisaoModeracaoResponse toDecisao(DecisaoModeracao decisao, List<Sancao> sancoes) {
        return new DecisaoModeracaoResponse(decisao.getId(), decisao.getVeredito(), decisao.getFundamento(),
                decisao.getAdministradorId(), decisao.getDecididaEm(), toSancoes(sancoes));
    }

    private static List<SancaoResponse> toSancoes(List<Sancao> sancoes) {
        return sancoes.stream()
                .map(sancao -> new SancaoResponse(sancao.getId(), sancao.getTipo(), sancao.getMotivo(),
                        sancao.getInicioEm(), sancao.getFimEm(), sancao.getAvaliacaoId(), sancao.getAplicadaEm()))
                .toList();
    }
}
