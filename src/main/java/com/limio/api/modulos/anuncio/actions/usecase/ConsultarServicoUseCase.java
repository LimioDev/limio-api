package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.ConsultaAvaliacoes;
import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ConsultaChatDoServico;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaPlanoPrestador;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoAvaliacoes;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.comum.portas.ResumoChatDoServico;
import com.limio.api.comum.portas.enums.Plano;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.OutraParteDoServico;
import com.limio.api.modulos.anuncio.ServicoCombinado;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.excecao.ServicoNaoEncontradoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/**
 * UC "Consultar o serviço combinado" (ETI-86, US39): o Empregador e o Prestador escolhido veem o anúncio
 * depois da escolha, com o contato um do outro liberado. Qualquer outra pessoa — inclusive um Prestador que
 * nunca foi escolhido — recebe a mesma resposta de inexistente, sem revelar se o anúncio existe.
 *
 * <p>A candidatura escolhida é achada por {@code escolhidaEm != null}, não pelo {@code status} atual: o
 * acesso continua valendo depois que o serviço conclui ou o anúncio cancela (UC "Empregador cancelar após
 * combinação" — o contato já visto fica no histórico de quem foi escolhido).
 */
@Service
public class ConsultarServicoUseCase {

    /** ETI-99/ETI-100: limite de reagendamentos aceitos por anúncio. Não existe usecase que crie proposta ainda. */
    static final int LIMITE_REAGENDAMENTOS = 2;

    /** "Até quando o Empregador pode cancelar" (ETI-86): a carência de um reagendamento aceito é do ETI-94/95. */
    static final Duration PRAZO_CANCELAMENTO = Duration.ofHours(24);

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ReagendamentoService reagendamentoService;
    private final ConsultaCategoria consultaCategoria;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaFotoPerfil consultaFotoPerfil;
    private final ConsultaPlanoPrestador consultaPlanoPrestador;
    private final ConsultaChatDoServico consultaChatDoServico;
    private final ConsultaAvaliacoes consultaAvaliacoes;

    public ConsultarServicoUseCase(AnuncioService anuncioService,
            AplicacaoService aplicacaoService,
            ReagendamentoService reagendamentoService,
            ConsultaCategoria consultaCategoria,
            ConsultaDadosUsuario consultaDadosUsuario,
            ConsultaFotoPerfil consultaFotoPerfil,
            ConsultaPlanoPrestador consultaPlanoPrestador,
            ConsultaChatDoServico consultaChatDoServico,
            ConsultaAvaliacoes consultaAvaliacoes) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.reagendamentoService = reagendamentoService;
        this.consultaCategoria = consultaCategoria;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaFotoPerfil = consultaFotoPerfil;
        this.consultaPlanoPrestador = consultaPlanoPrestador;
        this.consultaChatDoServico = consultaChatDoServico;
        this.consultaAvaliacoes = consultaAvaliacoes;
    }

    @Transactional(readOnly = true)
    public ServicoCombinado executar(UUID usuarioId, UUID anuncioId) {
        Anuncio anuncio = anuncioService.buscarPorId(anuncioId).orElseThrow(ServicoNaoEncontradoException::new);
        Aplicacao escolhida = aplicacaoService.buscarEscolhidaDoAnuncio(anuncioId)
                .orElseThrow(ServicoNaoEncontradoException::new);

        boolean souEmpregador = anuncio.pertenceA(usuarioId);
        boolean souPrestadorEscolhido = escolhida.getPrestadorId().equals(usuarioId);
        if (!souEmpregador && !souPrestadorEscolhido) {
            throw new ServicoNaoEncontradoException();
        }

        // Categoria nunca é apagada, só desativada: ausência aqui é dado corrompido, não cenário de negócio.
        ResumoCategoria categoria = consultaCategoria.buscarPorId(anuncio.getCategoriaId())
                .orElseThrow(() -> new IllegalStateException("categoria do anúncio não existe: " + anuncioId));

        OutraParteDoServico outraParte = souEmpregador
                ? prestadorVistoPeloEmpregador(escolhida.getPrestadorId())
                : empregadorVistoPeloPrestador(anuncio.getContratanteId());

        Reagendamento propostaPendente = reagendamentoService.buscarPendentesDosAnuncios(List.of(anuncioId))
                .stream().findFirst().orElse(null);

        ResumoChatDoServico chat = consultaChatDoServico.buscar(escolhida.getId(), usuarioId).orElse(null);
        Instant prazoCancelamentoEm = anuncio.getDataHoraPretendida().minus(PRAZO_CANCELAMENTO);

        return new ServicoCombinado(anuncio, escolhida.getId(), categoria, escolhida.getEscolhidaEm(),
                prazoCancelamentoEm, outraParte, reagendamentoService.contarAceitos(anuncioId), LIMITE_REAGENDAMENTOS,
                propostaPendente, chat);
    }

    /**
     * GOLD e reputação só são calculados pro Empregador ver do Prestador, como na lista de candidatos (ETI-67).
     * Vazio se a conta do Prestador já foi encerrada.
     */
    private OutraParteDoServico prestadorVistoPeloEmpregador(UUID prestadorId) {
        Optional<DadosUsuario> dados = consultaDadosUsuario.buscarPorId(prestadorId);
        if (dados.isEmpty()) {
            return OutraParteDoServico.CONTA_ENCERRADA;
        }
        boolean gold = consultaPlanoPrestador.planoAtual(prestadorId) == Plano.GOLD;
        ResumoAvaliacoes reputacao = consultaAvaliacoes.resumir(List.of(prestadorId))
                .getOrDefault(prestadorId, ResumoAvaliacoes.SEM_AVALIACOES);
        return new OutraParteDoServico(dados.get().nomeCompleto(), foto(prestadorId), dados.get().telefone(), gold,
                reputacao.quantidade(), reputacao.media());
    }

    /**
     * Sem GOLD (não existe GOLD de Empregador) e sem reputação (a dele já aparece no detalhe do mural).
     * Vazio se a conta do Empregador já foi encerrada.
     */
    private OutraParteDoServico empregadorVistoPeloPrestador(UUID empregadorId) {
        return consultaDadosUsuario.buscarPorId(empregadorId)
                .map(dados -> new OutraParteDoServico(dados.nomeCompleto(), foto(empregadorId), dados.telefone(),
                        null, null, null))
                .orElse(OutraParteDoServico.CONTA_ENCERRADA);
    }

    private LinkTemporario foto(UUID usuarioId) {
        return consultaFotoPerfil.buscarFoto(usuarioId).orElse(null);
    }
}
