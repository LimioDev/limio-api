package com.limio.api.modulos.anuncio.actions.usecase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.CandidatoResumido;
import com.limio.api.modulos.anuncio.FotoDoAnuncio;
import com.limio.api.modulos.anuncio.MeuAnuncio;
import com.limio.api.modulos.anuncio.actions.helper.NomeHelper;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;

/**
 * "Anúncio publicado" (tela 16, ETI-59): o anúncio inteiro pro dono, com endereço completo, fotos e os
 * primeiros candidatos. Anúncio de outra pessoa responde como inexistente.
 *
 * Os links das fotos são temporários e gerados a cada consulta. Storage fora do ar não derruba a tela: a foto
 * sai sem link.
 */
@Service
public class ConsultarMeuAnuncioUseCase {

    /** Quantos candidatos a tela mostra com nome abreviado e foto; os demais entram só no total. */
    static final int PRIMEIROS_CANDIDATOS = 4;

    private static final Logger log = LoggerFactory.getLogger(ConsultarMeuAnuncioUseCase.class);

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final MidiaService midiaService;
    private final ArmazenamentoService armazenamentoService;
    private final ConsultaCategoria consultaCategoria;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaFotoPerfil consultaFotoPerfil;

    public ConsultarMeuAnuncioUseCase(AnuncioService anuncioService,
            AplicacaoService aplicacaoService,
            MidiaService midiaService,
            ArmazenamentoService armazenamentoService,
            ConsultaCategoria consultaCategoria,
            ConsultaDadosUsuario consultaDadosUsuario,
            ConsultaFotoPerfil consultaFotoPerfil) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.midiaService = midiaService;
        this.armazenamentoService = armazenamentoService;
        this.consultaCategoria = consultaCategoria;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaFotoPerfil = consultaFotoPerfil;
    }

    @Transactional(readOnly = true)
    public MeuAnuncio executar(UUID usuarioId, UUID anuncioId) {
        Anuncio anuncio = anuncioService.buscarPorId(anuncioId)
                .filter(encontrado -> encontrado.pertenceA(usuarioId))
                .orElseThrow(AnuncioNaoEncontradoException::new);
        // Categoria nunca é apagada, só desativada: ausência aqui é dado corrompido, não cenário de negócio.
        ResumoCategoria categoria = consultaCategoria.buscarPorId(anuncio.getCategoriaId())
                .orElseThrow(() -> new IllegalStateException("categoria do anúncio não existe: " + anuncioId));

        List<FotoDoAnuncio> fotos = midiaService.buscarFotosAtivasDoAnuncio(anuncioId).stream()
                .map(foto -> new FotoDoAnuncio(foto.getId(), link(foto.getUrlS3()).orElse(null)))
                .toList();
        List<Aplicacao> enviadas = aplicacaoService.buscarEnviadasDoAnuncio(anuncioId);
        List<CandidatoResumido> primeiros = enviadas.stream()
                .limit(PRIMEIROS_CANDIDATOS)
                .map(this::resumir)
                .flatMap(Optional::stream)
                .toList();
        return new MeuAnuncio(anuncio, categoria, fotos, enviadas.size(), primeiros);
    }

    /** Vazio se a conta do prestador já não existe (encerrada). */
    private Optional<CandidatoResumido> resumir(Aplicacao aplicacao) {
        UUID prestadorId = aplicacao.getPrestadorId();
        return consultaDadosUsuario.buscarPorId(prestadorId)
                .map(DadosUsuario::nomeCompleto)
                .map(nome -> new CandidatoResumido(NomeHelper.abreviar(nome),
                        consultaFotoPerfil.buscarFoto(prestadorId).orElse(null)));
    }

    private Optional<LinkTemporario> link(String chave) {
        try {
            return Optional.of(armazenamentoService.gerarLink(chave));
        } catch (ArmazenamentoIndisponivelException e) {
            log.warn("link de mídia indisponível; anúncio segue sem a imagem");
            return Optional.empty();
        }
    }
}
