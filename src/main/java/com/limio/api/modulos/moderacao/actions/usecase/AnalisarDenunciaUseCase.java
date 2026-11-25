package com.limio.api.modulos.moderacao.actions.usecase;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.portas.ConsultaChat;
import com.limio.api.comum.portas.ConsultaContasParaModeracao;
import com.limio.api.comum.portas.ConsultaServico;
import com.limio.api.comum.portas.ContaParaModeracao;
import com.limio.api.comum.portas.MensagemOriginal;
import com.limio.api.comum.portas.PartesAnuncio;
import com.limio.api.comum.portas.ResumoServico;
import com.limio.api.modulos.moderacao.AnaliseDaDenuncia;
import com.limio.api.modulos.moderacao.denuncia.DecisaoModeracao;
import com.limio.api.modulos.moderacao.denuncia.DecisaoModeracaoService;
import com.limio.api.modulos.moderacao.denuncia.Denuncia;
import com.limio.api.modulos.moderacao.denuncia.DenunciaService;
import com.limio.api.modulos.moderacao.denuncia.Sancao;
import com.limio.api.modulos.moderacao.enums.TipoAlvoDenuncia;
import com.limio.api.modulos.moderacao.excecao.DenunciaNaoEncontradaException;

/**
 * Analisar denúncia (ETI-116, somente leitura): os dados como o denunciante preencheu, as duas contas (nome, papel e
 * situação, nunca contato nem documento), o alvo, a foto de comprovação, o chat com o texto original, as fotos do
 * check-out e o histórico do denunciado (outras denúncias e sanções). Serve também para a denúncia já decidida, com a
 * decisão.
 *
 * <p>O tipo do alvo e o chat mostrado saem do que o anúncio era na hora da denúncia, não do estado atual: uma denúncia
 * feita com o anúncio no mural continua {@code ANUNCIO} depois da escolha, sem o chat do Prestador escolhido depois.
 */
@Component
public class AnalisarDenunciaUseCase {

    private final DenunciaService denunciaService;
    private final DecisaoModeracaoService decisaoModeracaoService;
    private final ConsultaContasParaModeracao consultaContas;
    private final ConsultaServico consultaServico;
    private final ConsultaChat consultaChat;
    private final MidiaService midiaService;
    private final ArmazenamentoService armazenamentoService;

    public AnalisarDenunciaUseCase(DenunciaService denunciaService, DecisaoModeracaoService decisaoModeracaoService,
            ConsultaContasParaModeracao consultaContas, ConsultaServico consultaServico, ConsultaChat consultaChat,
            MidiaService midiaService, ArmazenamentoService armazenamentoService) {
        this.denunciaService = denunciaService;
        this.decisaoModeracaoService = decisaoModeracaoService;
        this.consultaContas = consultaContas;
        this.consultaServico = consultaServico;
        this.consultaChat = consultaChat;
        this.midiaService = midiaService;
        this.armazenamentoService = armazenamentoService;
    }

    @Transactional(readOnly = true)
    public AnaliseDaDenuncia executar(UUID denunciaId) {
        Denuncia denuncia = denunciaService.buscarPorId(denunciaId).orElseThrow(DenunciaNaoEncontradaException::new);
        Optional<ResumoServico> servico = Optional.ofNullable(denuncia.getAnuncioId())
                .flatMap(consultaServico::buscarPorId);
        Optional<PartesAnuncio> servicoNaDenuncia = Optional.ofNullable(denuncia.getAnuncioId())
                .flatMap(consultaServico::buscarPartes)
                .filter(partes -> partes.eraServicoEm(denuncia.getCriadoEm()));
        Map<UUID, ContaParaModeracao> contas = consultaContas.buscarPorIds(
                List.of(denuncia.getDenuncianteId(), denuncia.getDenunciadoId()));
        Optional<DecisaoModeracao> decisao = decisaoModeracaoService.buscarDaDenuncia(denunciaId);

        return new AnaliseDaDenuncia(
                denuncia,
                tipoAlvo(denuncia, servicoNaDenuncia.isPresent()),
                servico.map(ResumoServico::titulo).orElse(null),
                contas.get(denuncia.getDenuncianteId()),
                contas.get(denuncia.getDenunciadoId()),
                evidencia(denuncia),
                fotosCheckout(denuncia),
                chat(denuncia, servicoNaDenuncia),
                denunciaService.buscarOutrasContraODenunciado(denuncia),
                sancoesAnteriores(denuncia, decisao),
                decisao.orElse(null),
                decisao.map(d -> decisaoModeracaoService.buscarSancoesDaDecisao(d.getId())).orElse(List.of()));
    }

    /** A mensagem é o alvo mais específico; a denúncia de mensagem também guarda a candidatura e o anúncio. */
    private static TipoAlvoDenuncia tipoAlvo(Denuncia denuncia, boolean eraServico) {
        if (denuncia.getMensagemId() != null) {
            return TipoAlvoDenuncia.MENSAGEM;
        }
        if (denuncia.getCandidaturaId() != null) {
            return TipoAlvoDenuncia.CANDIDATURA;
        }
        if (denuncia.getAnuncioId() != null) {
            return eraServico ? TipoAlvoDenuncia.SERVICO : TipoAlvoDenuncia.ANUNCIO;
        }
        return TipoAlvoDenuncia.PERFIL;
    }

    private LinkTemporario evidencia(Denuncia denuncia) {
        if (denuncia.getEvidenciaId() == null) {
            return null;
        }
        Midia foto = midiaService.buscarPorIdOuFalhar(denuncia.getEvidenciaId());
        return armazenamentoService.gerarLinkSeDisponivel(foto.getUrlS3()).orElse(null);
    }

    private List<LinkTemporario> fotosCheckout(Denuncia denuncia) {
        if (denuncia.getAnuncioId() == null) {
            return List.of();
        }
        return midiaService.buscarFotosAtivasDoCheckout(denuncia.getAnuncioId()).stream()
                .flatMap(foto -> armazenamentoService.gerarLinkSeDisponivel(foto.getUrlS3()).stream())
                .toList();
    }

    /**
     * O chat da candidatura do contexto ou, na denúncia de serviço, as conversas do Prestador escolhido (mesmo com o
     * serviço cancelado depois).
     */
    private List<MensagemOriginal> chat(Denuncia denuncia, Optional<PartesAnuncio> servicoNaDenuncia) {
        if (denuncia.getCandidaturaId() != null) {
            return consultaChat.listarMensagensOriginais(denuncia.getCandidaturaId());
        }
        return servicoNaDenuncia
                .map(partes -> consultaChat.listarMensagensOriginaisDoServico(partes.anuncioId(), partes.escolhidoId()))
                .orElse(List.of());
    }

    /** As sanções desta própria decisão ficam fora do histórico: aparecem junto com a decisão. */
    private List<Sancao> sancoesAnteriores(Denuncia denuncia, Optional<DecisaoModeracao> decisao) {
        UUID decisaoId = decisao.map(DecisaoModeracao::getId).orElse(null);
        return decisaoModeracaoService.buscarSancoesDoUsuario(denuncia.getDenunciadoId()).stream()
                .filter(sancao -> !sancao.getDecisaoId().equals(decisaoId))
                .toList();
    }
}
