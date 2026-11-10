package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.PrestadorEscolhidoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaPlanoPrestador;
import com.limio.api.comum.portas.ConsumoCotaPrestador;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.helper.NomeHelper;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.excecao.AnuncioEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.CandidaturaEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.CandidaturaNaoEncontradaException;
import com.limio.api.modulos.anuncio.excecao.CotaEsgotadaCandidatoException;
import com.limio.api.modulos.anuncio.excecao.EscolhaConflitoException;

/**
 * UC — Empregador escolhe o Prestador do anúncio (ETI-69). Numa única transação: a candidatura escolhida vai de
 * {@code ENVIADA} pra {@code ESCOLHIDA} ({@code escolhidaEm}), as outras {@code ENVIADA} vão pra {@code ENCERRADA}
 * (as {@code RECUSADA} ficam como estão), a cota do Prestador FREE é consumida e {@link PrestadorEscolhidoEvent} é
 * publicado. O anúncio continua {@code PUBLICADO}: o que muda é a candidatura (ETI-86 e ETI-88 já assumem isso).
 * A liberação do telefone do Empregador e do endereço completo ao escolhido é consequência de gravar {@code
 * escolhidaEm}: {@code ConsultarServicoUseCase} só os entrega a quem tem a candidatura escolhida.
 *
 * <p>Ordem de aquisição das travas, a mesma de todo escritor do agregado (ver {@link EscopoTrava#ANUNCIO}):
 * <ol>
 *   <li>advisory lock {@code ANUNCIO}, antes de ler qualquer estado: duas escolhas, ou uma escolha contra recusa,
 *       desistência, candidatura ou cancelamento, entram em fila e a seguinte vê o estado já commitado;</li>
 *   <li>{@code FOR UPDATE} nas candidaturas {@code ENVIADA} do anúncio, por id: cobre quem não toma o advisory lock
 *       (por exemplo o encerramento por exclusão de conta) e lê a candidatura alvo já fresca;</li>
 *   <li>cota do Prestador, dentro da porta {@link ConsumoCotaPrestador} ({@code COTA_PRESTADOR}): o mesmo Prestador
 *       escolhido em dois anúncios ao mesmo tempo só se enfileira aqui, sem ciclo.</li>
 * </ol>
 * A cota é consumida antes de qualquer mutação das candidaturas: se esgotou, nada mudou. Qualquer falha depois dela
 * desfaz tudo junto (mesma transação).
 *
 * <p>Dono errado e anúncio inexistente respondem igual (404), e candidatura de outro anúncio também (404).
 */
@Service
public class EscolherPrestadorUseCase {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ConsultaPlanoPrestador consultaPlanoPrestador;
    private final ConsumoCotaPrestador consumoCotaPrestador;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;

    public EscolherPrestadorUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ConsultaPlanoPrestador consultaPlanoPrestador, ConsumoCotaPrestador consumoCotaPrestador,
            ConsultaDadosUsuario consultaDadosUsuario, ApplicationEventPublisher eventos, TravaTransacional trava) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.consultaPlanoPrestador = consultaPlanoPrestador;
        this.consumoCotaPrestador = consumoCotaPrestador;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.eventos = eventos;
        this.trava = trava;
    }

    @Transactional
    public Anuncio executar(UUID contratanteId, UUID anuncioId, UUID candidaturaId) {
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Anuncio anuncio = anuncioService.buscarPorId(anuncioId)
                .filter(encontrado -> encontrado.pertenceA(contratanteId))
                .orElseThrow(AnuncioNaoEncontradoException::new);
        if (anuncio.getStatus() != StatusAnuncio.PUBLICADO) {
            throw new AnuncioEstadoInvalidoException();
        }
        if (aplicacaoService.existeEscolhida(anuncioId)) {
            throw new EscolhaConflitoException();
        }

        List<Aplicacao> enviadas = aplicacaoService.buscarEnviadasDoAnuncioParaAtualizar(anuncioId);
        Aplicacao escolhida = enviadas.stream()
                .filter(candidatura -> candidatura.getId().equals(candidaturaId))
                .findFirst()
                .orElseThrow(() -> motivoDeNaoEstarEnviada(anuncioId, candidaturaId));

        Instant agora = Instant.now();
        consumirCotaSeFree(escolhida.getPrestadorId(), agora);

        escolhida.escolher(agora);
        aplicacaoService.salvarEscolha(escolhida);

        List<UUID> naoEscolhidas = new ArrayList<>();
        for (Aplicacao outra : enviadas) {
            if (outra == escolhida) {
                continue;
            }
            outra.encerrarPorOutraEscolha(agora);
            aplicacaoService.salvar(outra);
            naoEscolhidas.add(outra.getId());
        }

        eventos.publishEvent(new PrestadorEscolhidoEvent(anuncioId, anuncio.getTitulo(), escolhida.getId(),
                escolhida.getPrestadorId(), contratanteId, naoEscolhidas, agora));
        return anuncio;
    }

    private void consumirCotaSeFree(UUID prestadorId, Instant agora) {
        switch (consultaPlanoPrestador.planoAtual(prestadorId)) {
            case GOLD -> {
            }
            case FREE -> {
                if (!consumoCotaPrestador.consumirEscolha(prestadorId, agora)) {
                    throw new CotaEsgotadaCandidatoException(nomeDoCandidato(prestadorId));
                }
            }
        }
    }

    private String nomeDoCandidato(UUID prestadorId) {
        return consultaDadosUsuario.buscarPorId(prestadorId)
                .map(DadosUsuario::nomeCompleto)
                .map(NomeHelper::abreviar)
                .orElse("O candidato");
    }

    private RuntimeException motivoDeNaoEstarEnviada(UUID anuncioId, UUID candidaturaId) {
        return aplicacaoService.buscarPorId(candidaturaId)
                .filter(encontrada -> encontrada.getAnuncioId().equals(anuncioId))
                .<RuntimeException>map(encontrada -> new CandidaturaEstadoInvalidoException())
                .orElseGet(CandidaturaNaoEncontradaException::new);
    }
}
