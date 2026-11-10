package com.limio.api.modulos.perfil.actions.usecase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.AvaliacaoRecente;
import com.limio.api.comum.portas.ConsultaAvaliacoes;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaHistoricoDeServicos;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoAvaliacoes;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.comum.texto.NomeHelper;
import com.limio.api.modulos.perfil.PerfilPublico;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.excecao.PerfilPublicoNaoEncontradoException;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;

/**
 * UC "Consultar perfil público" (ETI-105): perfil e reputação de outra pessoa, sem dados de contato — qualquer
 * usuário logado, inclusive pro próprio perfil.
 *
 * <p>Usuário que nunca existiu lança {@link PerfilPublicoNaoEncontradoException} (404). Conta encerrada é um
 * caso diferente: devolve 200 com nome "Conta encerrada" (mesmo literal de {@code Usuario.encerrar} em
 * {@code modulos.auth} e {@link PerfilEmpregador#anonimizar}, nunca dado pessoal) — sem foto (expurgada no
 * encerramento) e sem especialidades (perfil de atuação é apagado no encerramento, {@code
 * AnonimizarPerfilDaContaEncerradaUseCase}). Quantidade/média de avaliações e serviços concluídos continuam
 * aparecendo: a linha de {@code PerfilEmpregador} e o histórico de {@code anuncio}/{@code aplicacao} não são
 * apagados, de propósito, pro histórico de reputação sobreviver ao encerramento.
 *
 * <p>Nome de exibição por papel: Empregador usa {@code PerfilEmpregador.nomeExibicao} (texto livre, escolhido
 * pelo próprio usuário — mesma fonte do painel e do mural). Prestador usa o nome completo do cadastro
 * abreviado por {@link NomeHelper} (mesma regra de "candidatos do anúncio", ETI-67): o Prestador não escolhe
 * como aparece, só o Empregador escolhe.
 *
 * <p>Quantidade/média de avaliações: {@code PerfilEmpregador}/{@code PerfilPrestador} têm as duas colunas
 * (ETI-107), gravadas por {@code RegistrarMediaAvaliacoesUseCase} sempre que {@code moderacao} publica uma
 * avaliação — aqui é só leitura, igual nos dois papéis. Avaliações recentes continuam vindo da porta
 * {@code ConsultaAvaliacoes}: a lista com nome do avaliador/comentário só existe na entity {@code Avaliacao},
 * não nas colunas agregadas do perfil.
 */
@Service
public class ConsultarPerfilPublicoUseCase {

    private static final int MAX_AVALIACOES_RECENTES = 5;

    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaFotoPerfil consultaFotoPerfil;
    private final ConsultaAvaliacoes consultaAvaliacoes;
    private final ConsultaHistoricoDeServicos consultaHistoricoDeServicos;
    private final PerfilEmpregadorService perfilEmpregadorService;
    private final PerfilPrestadorService perfilPrestadorService;

    public ConsultarPerfilPublicoUseCase(ConsultaDadosUsuario consultaDadosUsuario,
            ConsultaFotoPerfil consultaFotoPerfil,
            ConsultaAvaliacoes consultaAvaliacoes,
            ConsultaHistoricoDeServicos consultaHistoricoDeServicos,
            PerfilEmpregadorService perfilEmpregadorService,
            PerfilPrestadorService perfilPrestadorService) {
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaFotoPerfil = consultaFotoPerfil;
        this.consultaAvaliacoes = consultaAvaliacoes;
        this.consultaHistoricoDeServicos = consultaHistoricoDeServicos;
        this.perfilEmpregadorService = perfilEmpregadorService;
        this.perfilPrestadorService = perfilPrestadorService;
    }

    @Transactional(readOnly = true)
    public PerfilPublico executar(UUID usuarioId, PapelUsuario papel) {
        Optional<DadosUsuario> dados = consultaDadosUsuario.buscarPorId(usuarioId);
        if (dados.isEmpty() && !consultaDadosUsuario.existe(usuarioId)) {
            throw new PerfilPublicoNaoEncontradoException();
        }

        DadosDoPapel doPapel = dadosDoPapel(usuarioId, papel, dados);
        Optional<LinkTemporario> foto = consultaFotoPerfil.buscarFoto(usuarioId);
        List<AvaliacaoRecente> recentes = consultaAvaliacoes.listarRecentes(usuarioId, papel, MAX_AVALIACOES_RECENTES);
        int servicosConcluidos = consultaHistoricoDeServicos.contarConcluidos(usuarioId, papel);

        return new PerfilPublico(doPapel.nomeExibicao(),
                foto.map(LinkTemporario::url).orElse(null),
                foto.map(LinkTemporario::expiraEm).orElse(null),
                doPapel.avaliacoes().media(), doPapel.avaliacoes().quantidade(),
                servicosConcluidos, doPapel.especialidades(), recentes);
    }

    /** Nome, especialidades e avaliações dependem do mesmo registro (perfil do papel) — uma busca só por papel. */
    private DadosDoPapel dadosDoPapel(UUID usuarioId, PapelUsuario papel, Optional<DadosUsuario> dados) {
        return switch (papel) {
            case EMPREGADOR -> {
                Optional<PerfilEmpregador> perfil = perfilEmpregadorService.buscarPorUsuario(usuarioId);
                String nomeExibicao = dados.isPresent()
                        ? perfil.map(PerfilEmpregador::getNomeExibicao)
                                .orElseGet(() -> PerfilEmpregador.nomeExibicaoInicialDe(dados.get().nomeCompleto()))
                        : PerfilEmpregador.NOME_CONTA_ENCERRADA;
                yield new DadosDoPapel(nomeExibicao, List.of(), avaliacoesDoEmpregador(perfil));
            }
            case PRESTADOR -> {
                Optional<PerfilPrestador> perfil = perfilPrestadorService.buscarPorUsuario(usuarioId);
                String nomeExibicao = dados.map(d -> NomeHelper.abreviar(d.nomeCompleto()))
                        .orElse(PerfilEmpregador.NOME_CONTA_ENCERRADA);
                List<String> especialidades = perfil.map(p -> p.getEspecialidades().stream()
                        .sorted(Categoria.ORDEM_EXIBICAO)
                        .map(Categoria::getNome)
                        .toList())
                        .orElse(List.of());
                yield new DadosDoPapel(nomeExibicao, especialidades, avaliacoesDoPrestador(perfil));
            }
        };
    }

    private static ResumoAvaliacoes avaliacoesDoEmpregador(Optional<PerfilEmpregador> perfil) {
        return perfil.map(p -> new ResumoAvaliacoes(p.getQtdAvaliacoes(), p.getMediaAvaliacoes()))
                .orElse(ResumoAvaliacoes.SEM_AVALIACOES);
    }

    private static ResumoAvaliacoes avaliacoesDoPrestador(Optional<PerfilPrestador> perfil) {
        return perfil.map(p -> new ResumoAvaliacoes(p.getQtdAvaliacoes(), p.getMediaAvaliacoes()))
                .orElse(ResumoAvaliacoes.SEM_AVALIACOES);
    }

    private record DadosDoPapel(String nomeExibicao, List<String> especialidades, ResumoAvaliacoes avaliacoes) {
    }
}
