package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.ServicoConcluidoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
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
 * {@link ServicoConcluidoEvent} é publicado; o único consumidor previsto, ainda não implementado, é
 * {@code notificacao} (avisar o Prestador, ETI-112). {@code moderacao} (ETI-107, já na develop) não escuta
 * este evento — lê {@code validadoEm} direto pela porta {@code ConsultaServico}/{@code ResumoServico}.
 *
 * A trava {@link EscopoTrava#ANUNCIO} (advisory lock, a mesma de editar, cancelar, iniciar, candidatar-se,
 * desistir, recusar e escolher candidato, ETI-61/ETI-69) vem antes de ler o anúncio: barra o clique duplo (a
 * segunda requisição encontra {@code CONCLUIDO} e recebe 409) e coloca a validação na mesma fila de todo
 * escritor do agregado.
 *
 * Só o Empregador dono do anúncio valida; os demais recebem o mesmo 404 {@code SERVICO_NAO_ENCONTRADO} de
 * um id inexistente, mesmo critério de {@code ConsultarServicoUseCase} (ETI-86).
 */
@Service
public class ValidarServicoUseCase {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;

    public ValidarServicoUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ApplicationEventPublisher eventos, TravaTransacional trava) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.eventos = eventos;
        this.trava = trava;
    }

    @Transactional
    public Anuncio executar(UUID anuncioId, UUID contratanteId, ModoValidacao modo) {
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Anuncio anuncio = anuncioService.buscarPorId(anuncioId)
                .filter(encontrado -> encontrado.pertenceA(contratanteId))
                .orElseThrow(ServicoNaoEncontradoException::new);

        UUID prestadorId = aplicacaoService.buscarEscolhidaDoAnuncio(anuncioId)
                .orElseThrow(ServicoNaoEncontradoException::new)
                .getPrestadorId();

        garantirQuePermite(anuncio, modo);

        Instant agora = Instant.now();
        anuncio.validar(agora);
        Anuncio salvo = anuncioService.salvar(anuncio);

        eventos.publishEvent(
                new ServicoConcluidoEvent(anuncioId, anuncio.getTitulo(), contratanteId, prestadorId, agora));
        return salvo;
    }

    /**
     * Cada modo lança a sua própria exceção quando nega, em vez de calcular um {@code boolean} geral e só
     * depois decidir qual delas: {@code PELA_FOTO} tem dois motivos de recusa possíveis
     * ({@link ValidacaoSemCheckoutException} sem check-out, {@link ServicoEstadoInvalidoException} já
     * concluído/encerrado) e {@code PRESENCIAL} só um.
     */
    private static void garantirQuePermite(Anuncio anuncio, ModoValidacao modo) {
        switch (modo) {
            case PRESENCIAL -> {
                if (!anuncio.servicoEmAberto()) {
                    throw new ServicoEstadoInvalidoException();
                }
            }
            case PELA_FOTO -> {
                if (!anuncio.permiteValidacaoPelaFoto()) {
                    throw anuncio.servicoEmAberto() ? new ValidacaoSemCheckoutException()
                            : new ServicoEstadoInvalidoException();
                }
            }
        }
    }
}
