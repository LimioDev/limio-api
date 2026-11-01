package com.limio.api.modulos.anuncio.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.service.ValidacaoDoAnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.excecao.AnuncioEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;

/**
 * UC19 — Empregador corrige o anúncio antes de escolher alguém (ETI-61, RF26). Só vale com o anúncio
 * {@code PUBLICADO} e sem candidatura escolhida ({@link Anuncio#permiteAlteracao}); depois disso a mudança de
 * data e hora é reagendamento, que não é este caso de uso.
 *
 * Dono errado e anúncio inexistente têm a mesma resposta (404), igual a {@link CancelarAnuncioUseCase}. Data
 * futura e categoria ativa são conferidas antes da trava, como na criação. A trava {@link EscopoTrava#ANUNCIO}
 * serializa a edição com a escolha do candidato (que deve tomar a mesma trava): o estado é lido depois dela,
 * então uma escolha já confirmada barra a edição, e uma edição já confirmada fica visível pra escolha.
 */
@Service
public class EditarAnuncioUseCase {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ValidacaoDoAnuncioService validacaoDoAnuncio;
    private final TravaTransacional trava;

    public EditarAnuncioUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ValidacaoDoAnuncioService validacaoDoAnuncio, TravaTransacional trava) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.validacaoDoAnuncio = validacaoDoAnuncio;
        this.trava = trava;
    }

    /** @param novosDados categoria, título, descrição, data e opcionais vindos da tela; o resto do anúncio não muda */
    @Transactional
    public Anuncio executar(UUID usuarioId, UUID anuncioId, Anuncio novosDados) {
        validacaoDoAnuncio.exigirDataFutura(novosDados.getDataHoraPretendida());
        validacaoDoAnuncio.exigirCategoriaAtiva(novosDados.getCategoriaId());

        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Anuncio anuncio = anuncioService.buscarPorId(anuncioId)
                .filter(encontrado -> encontrado.pertenceA(usuarioId))
                .orElseThrow(AnuncioNaoEncontradoException::new);
        if (!anuncio.permiteAlteracao(aplicacaoService.existeEscolhida(anuncioId))) {
            throw new AnuncioEstadoInvalidoException();
        }

        anuncio.editar(novosDados);
        return anuncioService.salvar(anuncio);
    }
}
