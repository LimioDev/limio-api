package com.limio.api.modulos.anuncio.actions.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.ImagemHelper;
import com.limio.api.comum.midia.ImagemHelper.ImagemProcessada;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.excecao.FotoInvalidaException;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.FotoDoAnuncio;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.eventos.FotoAnuncioEnviadaEvent;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.FotosNaoPermitidasException;

/**
 * UC "Anexar fotos ao anúncio" (ETI-59): o dono envia uma ou mais fotos, com o mesmo tratamento da foto de
 * perfil (formato pelo conteúdo, EXIF removido — {@link ImagemHelper}).
 *
 * Tudo ou nada: todas as fotos são conferidas antes do primeiro upload, e se algo falhar depois dele a
 * transação desfaz e os objetos já enviados saem do bucket ({@link FotoAnuncioEnviadaEvent}). Envios
 * simultâneos ao mesmo anúncio se enfileiram, pra o limite de quantidade valer.
 */
@Service
public class AnexarFotosAnuncioUseCase {

    static final String PREFIXO_CHAVE = "fotos-anuncio/";

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final MidiaService midiaService;
    private final ArmazenamentoService armazenamentoService;
    private final ApplicationEventPublisher eventos;
    private final long tamanhoMaximoBytes;
    private final int quantidadeMaxima;

    public AnexarFotosAnuncioUseCase(AnuncioService anuncioService,
            AplicacaoService aplicacaoService,
            MidiaService midiaService,
            ArmazenamentoService armazenamentoService,
            ApplicationEventPublisher eventos,
            @Value("${anuncio.foto.tamanho-maximo}") DataSize tamanhoMaximo,
            @Value("${anuncio.foto.quantidade-maxima}") int quantidadeMaxima) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.midiaService = midiaService;
        this.armazenamentoService = armazenamentoService;
        this.eventos = eventos;
        this.tamanhoMaximoBytes = tamanhoMaximo.toBytes();
        this.quantidadeMaxima = quantidadeMaxima;
    }

    /** @return só as fotos gravadas neste envio, na ordem em que vieram */
    @Transactional
    public List<FotoDoAnuncio> executar(UUID usuarioId, UUID anuncioId, List<byte[]> arquivos) {
        if (arquivos.isEmpty()) {
            throw FotoInvalidaException.vazia();
        }
        Anuncio anuncio = anuncioService.buscarPorId(anuncioId)
                .filter(encontrado -> encontrado.pertenceA(usuarioId))
                .orElseThrow(AnuncioNaoEncontradoException::new);

        midiaService.travarFotosAnuncio(anuncioId);
        if (!anuncio.aceitaFotos(aplicacaoService.existeEscolhida(anuncioId))) {
            throw new FotosNaoPermitidasException();
        }
        if (midiaService.buscarFotosAtivasDoAnuncio(anuncioId).size() + arquivos.size() > quantidadeMaxima) {
            throw FotoInvalidaException.quantidadeExcedida(quantidadeMaxima);
        }
        List<ImagemProcessada> imagens = arquivos.stream().map(this::processar).toList();

        List<FotoDoAnuncio> gravadas = new ArrayList<>();
        for (ImagemProcessada imagem : imagens) {
            String chave = PREFIXO_CHAVE + anuncioId + "/" + UUID.randomUUID() + "." + imagem.extensao();
            armazenamentoService.enviar(chave, imagem.conteudo(), imagem.mime());
            // Publicado logo após cada upload: se algo abaixo falhar, o rollback apaga o objeto.
            eventos.publishEvent(new FotoAnuncioEnviadaEvent(chave));
            Midia midia = midiaService.salvar(Midia.fotoAnuncio(usuarioId, anuncioId, chave, imagem.mime(),
                    imagem.largura(), imagem.altura(), imagem.conteudo().length));
            gravadas.add(new FotoDoAnuncio(midia.getId(), armazenamentoService.gerarLink(chave)));
        }
        return gravadas;
    }

    private ImagemProcessada processar(byte[] arquivo) {
        if (arquivo == null || arquivo.length == 0) {
            throw FotoInvalidaException.vazia();
        }
        if (arquivo.length > tamanhoMaximoBytes) {
            throw FotoInvalidaException.grandeDemais(tamanhoMaximoBytes);
        }
        return ImagemHelper.processar(arquivo).orElseThrow(FotoInvalidaException::formatoNaoAceito);
    }
}
