package com.limio.api.modulos.anuncio.actions.helper;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.excecao.ReagendamentoNaoEncontradoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/**
 * Passo comum de responder e cancelar uma proposta de reagendamento: o {@code anuncioId} sai de uma consulta escalar,
 * a trava advisory {@code ANUNCIO} é tomada e só então a proposta, o anúncio e o Prestador escolhido são lidos (nunca
 * uma cópia anterior à trava). Quem não é parte do serviço recebe 404, como se a proposta não existisse.
 *
 * <p>Também guarda a regra de "parte do serviço" ({@link #ehParteDoServico}), usada por propor, consultar, responder
 * e cancelar — um lugar só pra regra de acesso e pra ordem trava → leitura.
 *
 * <p>Precisa rodar dentro de transação (a trava é {@code MANDATORY}).
 */
@Component
public class PropostaSobTravaHelper {

    /** Proposta, anúncio e Prestador escolhido ({@code null} se não houver), lidos sob a trava do anúncio. */
    public record PropostaCarregada(Reagendamento proposta, Anuncio anuncio, UUID prestadorId) {
    }

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ReagendamentoService reagendamentoService;
    private final TravaTransacional trava;

    public PropostaSobTravaHelper(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ReagendamentoService reagendamentoService, TravaTransacional trava) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.reagendamentoService = reagendamentoService;
        this.trava = trava;
    }

    /** @throws ReagendamentoNaoEncontradoException proposta/anúncio inexistente, ou {@code usuarioId} não é parte */
    public PropostaCarregada carregarParaParte(UUID usuarioId, UUID reagendamentoId) {
        UUID anuncioId = reagendamentoService.buscarAnuncioId(reagendamentoId)
                .orElseThrow(ReagendamentoNaoEncontradoException::new);
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Reagendamento proposta = reagendamentoService.buscarPorId(reagendamentoId)
                .orElseThrow(ReagendamentoNaoEncontradoException::new);
        Anuncio anuncio = anuncioService.buscarPorId(anuncioId).orElseThrow(ReagendamentoNaoEncontradoException::new);
        UUID prestadorId = aplicacaoService.buscarPrestadorEscolhido(anuncioId).orElse(null);

        if (!ehParteDoServico(anuncio, prestadorId, usuarioId)) {
            throw new ReagendamentoNaoEncontradoException();
        }
        return new PropostaCarregada(proposta, anuncio, prestadorId);
    }

    /**
     * O Empregador do anúncio ou o Prestador escolhido, inclusive depois do check-out ou do cancelamento do serviço
     * (esses casos caem em 409 de estado adiante, não em 404). {@code prestadorEscolhidoId} pode ser {@code null}.
     */
    public static boolean ehParteDoServico(Anuncio anuncio, UUID prestadorEscolhidoId, UUID usuarioId) {
        return anuncio.pertenceA(usuarioId) || usuarioId.equals(prestadorEscolhidoId);
    }
}
