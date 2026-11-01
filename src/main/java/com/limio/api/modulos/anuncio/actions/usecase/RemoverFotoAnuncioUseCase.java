package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.eventos.FotoAnuncioRemovidaEvent;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.FotoNaoEncontradaException;
import com.limio.api.modulos.anuncio.excecao.FotosNaoPermitidasException;

/**
 * UC19 (parte das fotos, ETI-61) — o dono remove uma foto do anúncio enquanto ele ainda é editável
 * ({@link Anuncio#permiteAlteracao}). Mesmo padrão da troca da foto de perfil: a {@code Midia} é expurgada na
 * transação ({@code expurgoEm}) e o objeto só sai do bucket depois do commit ({@link FotoAnuncioRemovidaEvent}),
 * pra uma falha no banco nunca deixar uma foto ativa sem arquivo.
 *
 * Trava {@link EscopoTrava#ANUNCIO} e depois {@link EscopoTrava#FOTOS_ANUNCIO}, a mesma ordem de quem anexa
 * e de quem edita, pra não ter deadlock e pra a escolha do candidato barrar a remoção.
 */
@Service
public class RemoverFotoAnuncioUseCase {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final MidiaService midiaService;
    private final TravaTransacional trava;
    private final ApplicationEventPublisher eventos;

    public RemoverFotoAnuncioUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            MidiaService midiaService, TravaTransacional trava, ApplicationEventPublisher eventos) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.midiaService = midiaService;
        this.trava = trava;
        this.eventos = eventos;
    }

    @Transactional
    public void executar(UUID usuarioId, UUID anuncioId, UUID fotoId) {
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());
        Anuncio anuncio = anuncioService.buscarPorId(anuncioId)
                .filter(encontrado -> encontrado.pertenceA(usuarioId))
                .orElseThrow(AnuncioNaoEncontradoException::new);

        midiaService.travarFotosAnuncio(anuncioId);
        if (!anuncio.permiteAlteracao(aplicacaoService.existeEscolhida(anuncioId))) {
            throw new FotosNaoPermitidasException();
        }
        Midia foto = midiaService.buscarFotoAtivaDoAnuncio(anuncioId, fotoId)
                .orElseThrow(FotoNaoEncontradaException::new);

        foto.expurgar(Instant.now());
        midiaService.salvarAgora(foto);
        eventos.publishEvent(new FotoAnuncioRemovidaEvent(foto.getUrlS3()));
    }
}
