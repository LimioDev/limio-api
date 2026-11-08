package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import com.limio.api.comum.eventos.CheckoutEnviadoEvent;
import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.ImagemHelper;
import com.limio.api.comum.midia.ImagemHelper.ImagemProcessada;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.excecao.FotoInvalidaException;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.eventos.FotoAnuncioEnviadaEvent;
import com.limio.api.modulos.anuncio.excecao.FotoObrigatoriaException;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;

/**
 * UC — Prestador escolhido faz o check-out com fotos do serviço pronto (ETI-90): as fotos viram {@code Midia}
 * {@code EVIDENCIA_CHECKOUT}, o anúncio e a candidatura vão pra {@code AGUARDANDO_VALIDACAO}, {@code checkoutEm}
 * é gravado e {@link CheckoutEnviadoEvent} é publicado (o aviso ao Empregador é a ETI-112). Nunca conclui o
 * serviço: isso é do Empregador (ETI-92/93) ou do silêncio (ETI-98).
 *
 * Ordem das recusas: serviço inexistente e quem não é o escolhido têm a mesma resposta (404), antes de qualquer
 * conferência de foto, pra não revelar nada a terceiros. Depois o estado (409), depois as fotos (422).
 *
 * Mesmo tratamento de imagem e mesmos limites das fotos do anúncio ({@code anuncio.foto.*}). Tudo ou nada: as
 * fotos são conferidas antes do primeiro upload, e se algo falhar depois a transação desfaz e os objetos já
 * enviados saem do bucket ({@link FotoAnuncioEnviadaEvent}, mesma limpeza das fotos do anúncio). A trava no
 * anúncio barra o clique duplo: a segunda requisição encontra {@code AGUARDANDO_VALIDACAO} e recebe 409.
 */
@Service
public class RealizarCheckoutUseCase {

    static final String PREFIXO_CHAVE = "fotos-checkout/";

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final MidiaService midiaService;
    private final ArmazenamentoService armazenamentoService;
    private final ApplicationEventPublisher eventos;
    private final long tamanhoMaximoBytes;
    private final int quantidadeMaxima;

    public RealizarCheckoutUseCase(AnuncioService anuncioService,
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

    @Transactional
    public Anuncio executar(UUID anuncioId, UUID prestadorId, List<byte[]> arquivos, String observacao) {
        Anuncio anuncio = anuncioService.buscarParaAtualizar(anuncioId)
                .orElseThrow(ServicoNaoEncontradoException::new);

        Aplicacao escolhida = aplicacaoService.buscarDoPrestadorNoAnuncio(anuncioId, prestadorId)
                .filter(Aplicacao::isEscolhida)
                .orElseThrow(ServicoNaoEncontradoException::new);

        if (!anuncio.permiteCheckout()) {
            throw new ServicoEstadoInvalidoException();
        }

        if (arquivos.isEmpty()) {
            throw new FotoObrigatoriaException();
        }
        if (arquivos.size() > quantidadeMaxima) {
            throw FotoInvalidaException.quantidadeExcedida(quantidadeMaxima);
        }
        List<ImagemProcessada> imagens = arquivos.stream().map(this::processar).toList();

        for (ImagemProcessada imagem : imagens) {
            String chave = PREFIXO_CHAVE + anuncioId + "/" + UUID.randomUUID() + "." + imagem.extensao();
            armazenamentoService.enviar(chave, imagem.conteudo(), imagem.mime());
            // Publicado logo após cada upload: se algo abaixo falhar, o rollback apaga o objeto.
            eventos.publishEvent(new FotoAnuncioEnviadaEvent(chave));
            midiaService.salvar(Midia.evidenciaCheckout(prestadorId, anuncioId, chave, imagem.mime(),
                    imagem.largura(), imagem.altura(), imagem.conteudo().length));
        }

        Instant agora = Instant.now();
        anuncio.registrarCheckout(agora, observacao);
        escolhida.registrarCheckout();
        aplicacaoService.salvar(escolhida);
        Anuncio salvo = anuncioService.salvar(anuncio);

        eventos.publishEvent(new CheckoutEnviadoEvent(anuncioId, anuncio.getTitulo(), anuncio.getContratanteId(),
                prestadorId, agora));
        return salvo;
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
