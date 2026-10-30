package com.limio.api.modulos.anuncio.actions.usecase;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaAvaliacoes;
import com.limio.api.comum.portas.ConsultaChat;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaEspecialidades;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaPlanoPrestador;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoAvaliacoes;
import com.limio.api.comum.portas.enums.Plano;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.CandidatoDoAnuncio;
import com.limio.api.modulos.anuncio.actions.helper.NomeHelper;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;

/**
 * Lista os candidatos do anúncio pro dono (ETI-67): as candidaturas ainda sem resposta, da mais antiga pra mais
 * nova. Anúncio de outra pessoa responde como inexistente; anúncio sem candidatos devolve lista vazia.
 *
 * Candidato cuja conta já foi encerrada fica fora da lista, como no card do anúncio.
 */
@Service
public class ListarCandidatosUseCase {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaFotoPerfil consultaFotoPerfil;
    private final ConsultaPlanoPrestador consultaPlanoPrestador;
    private final ConsultaEspecialidades consultaEspecialidades;
    private final ConsultaAvaliacoes consultaAvaliacoes;
    private final ConsultaChat consultaChat;

    public ListarCandidatosUseCase(AnuncioService anuncioService,
            AplicacaoService aplicacaoService,
            ConsultaDadosUsuario consultaDadosUsuario,
            ConsultaFotoPerfil consultaFotoPerfil,
            ConsultaPlanoPrestador consultaPlanoPrestador,
            ConsultaEspecialidades consultaEspecialidades,
            ConsultaAvaliacoes consultaAvaliacoes,
            ConsultaChat consultaChat) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaFotoPerfil = consultaFotoPerfil;
        this.consultaPlanoPrestador = consultaPlanoPrestador;
        this.consultaEspecialidades = consultaEspecialidades;
        this.consultaAvaliacoes = consultaAvaliacoes;
        this.consultaChat = consultaChat;
    }

    @Transactional(readOnly = true)
    public List<CandidatoDoAnuncio> executar(UUID usuarioId, UUID anuncioId) {
        anuncioService.buscarPorId(anuncioId)
                .filter(encontrado -> encontrado.pertenceA(usuarioId))
                .orElseThrow(AnuncioNaoEncontradoException::new);

        List<Aplicacao> enviadas = aplicacaoService.buscarEnviadasDoAnuncio(anuncioId);
        if (enviadas.isEmpty()) {
            return List.of();
        }
        List<UUID> prestadorIds = enviadas.stream().map(Aplicacao::getPrestadorId).toList();
        List<UUID> candidaturaIds = enviadas.stream().map(Aplicacao::getId).toList();
        Map<UUID, List<String>> especialidades = consultaEspecialidades.listarNomes(prestadorIds);
        Map<UUID, ResumoAvaliacoes> avaliacoes = consultaAvaliacoes.resumir(prestadorIds);
        Map<UUID, UUID> chats = consultaChat.buscarIdsPorCandidatura(candidaturaIds);

        return enviadas.stream()
                .map(aplicacao -> montar(aplicacao, especialidades, avaliacoes, chats))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<CandidatoDoAnuncio> montar(Aplicacao aplicacao, Map<UUID, List<String>> especialidades,
            Map<UUID, ResumoAvaliacoes> avaliacoes, Map<UUID, UUID> chats) {
        UUID prestadorId = aplicacao.getPrestadorId();
        return consultaDadosUsuario.buscarPorId(prestadorId)
                .map(DadosUsuario::nomeCompleto)
                .map(nome -> {
                    ResumoAvaliacoes resumo = avaliacoes.getOrDefault(prestadorId, ResumoAvaliacoes.SEM_AVALIACOES);
                    return new CandidatoDoAnuncio(
                            aplicacao.getId(),
                            NomeHelper.abreviar(nome),
                            consultaFotoPerfil.buscarFoto(prestadorId).orElse(null),
                            especialidades.getOrDefault(prestadorId, List.of()),
                            consultaPlanoPrestador.planoAtual(prestadorId) == Plano.GOLD,
                            resumo.quantidade(),
                            resumo.media(),
                            chats.get(aplicacao.getId()));
                });
    }
}
