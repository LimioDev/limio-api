package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.ServicoConcluidoEvent;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.ModoValidacao;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.ValidacaoSemCheckoutException;

/**
 * UC — Empregador valida o serviço, presencial ou pela foto (ETI-92): o anúncio vai pra {@code CONCLUIDO}
 * e {@code validadoEm} é preenchido. Não registra nem confere pagamento (UC) — isso fica fora do LIMIO.
 * {@link ServicoConcluidoEvent} é publicado, que os módulos {@code moderacao} (abrir avaliação das duas
 * partes, ETI-107/ETI-108) e {@code notificacao} (avisar o Prestador, ETI-112) escutam, nenhum dos dois
 * implementado ainda.
 *
 * A trava no anúncio barra o clique duplo, mesmo idioma de {@code IniciarServicoUseCase} (ETI-88).
 *
 * Só o Empregador dono do anúncio valida; os demais recebem o mesmo 404 {@code SERVICO_NAO_ENCONTRADO} de
 * um id inexistente, mesmo critério de {@code ConsultarServicoUseCase} (ETI-86).
 */
@Service
public class ValidarServicoUseCase {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ApplicationEventPublisher eventos;

    public ValidarServicoUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ApplicationEventPublisher eventos) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.eventos = eventos;
    }

    @Transactional
    public Anuncio executar(UUID anuncioId, UUID contratanteId, ModoValidacao modo) {
        Anuncio anuncio = anuncioService.buscarParaAtualizar(anuncioId)
                .filter(encontrado -> encontrado.pertenceA(contratanteId))
                .orElseThrow(ServicoNaoEncontradoException::new);

        UUID prestadorId = aplicacaoService.buscarEscolhidaDoAnuncio(anuncioId)
                .orElseThrow(ServicoNaoEncontradoException::new)
                .getPrestadorId();

        boolean permite = switch (modo) {
            case PRESENCIAL -> anuncio.permiteValidacaoPresencial();
            case PELA_FOTO -> anuncio.permiteValidacaoPelaFoto();
        };
        if (!permite) {
            boolean semCheckout = modo == ModoValidacao.PELA_FOTO && anuncio.aindaNaoTeveCheckout();
            throw semCheckout ? new ValidacaoSemCheckoutException() : new ServicoEstadoInvalidoException();
        }

        Instant agora = Instant.now();
        anuncio.validar(agora);
        Anuncio salvo = anuncioService.salvar(anuncio);

        eventos.publishEvent(
                new ServicoConcluidoEvent(anuncioId, anuncio.getTitulo(), contratanteId, prestadorId, agora));
        return salvo;
    }
}
