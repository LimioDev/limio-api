package com.limio.api.modulos.anuncio.actions.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.FotoDoCheckout;
import com.limio.api.modulos.anuncio.ValidacaoDoServico;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;

/**
 * UC "Consultar a validação" (ETI-92): o Empregador vê as fotos do check-out (se houver), quando o
 * check-out foi feito e o nome do Prestador escolhido, antes de confirmar a validação. Quem não é o
 * Empregador dono recebe o mesmo 404 {@code SERVICO_NAO_ENCONTRADO} de um id inexistente.
 */
@Service
public class ConsultarValidacaoUseCase {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final MidiaService midiaService;
    private final ArmazenamentoService armazenamentoService;
    private final ConsultaDadosUsuario consultaDadosUsuario;

    public ConsultarValidacaoUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            MidiaService midiaService, ArmazenamentoService armazenamentoService,
            ConsultaDadosUsuario consultaDadosUsuario) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.midiaService = midiaService;
        this.armazenamentoService = armazenamentoService;
        this.consultaDadosUsuario = consultaDadosUsuario;
    }

    @Transactional(readOnly = true)
    public ValidacaoDoServico executar(UUID anuncioId, UUID contratanteId) {
        Anuncio anuncio = anuncioService.buscarPorId(anuncioId)
                .filter(encontrado -> encontrado.pertenceA(contratanteId))
                .orElseThrow(ServicoNaoEncontradoException::new);

        Aplicacao escolhida = aplicacaoService.buscarEscolhidaDoAnuncio(anuncioId)
                .orElseThrow(ServicoNaoEncontradoException::new);

        List<FotoDoCheckout> fotos = midiaService.buscarFotosAtivasDoCheckout(anuncioId).stream()
                .map(foto -> new FotoDoCheckout(foto.getId(),
                        armazenamentoService.gerarLinkSeDisponivel(foto.getUrlS3()).orElse(null)))
                .toList();

        String nomePrestador = consultaDadosUsuario.buscarPorId(escolhida.getPrestadorId())
                .map(DadosUsuario::nomeCompleto)
                .orElse(null);

        return new ValidacaoDoServico(anuncio, fotos, nomePrestador);
    }
}
