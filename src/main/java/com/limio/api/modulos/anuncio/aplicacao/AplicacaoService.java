package com.limio.api.modulos.anuncio.aplicacao;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.anuncio.enums.AbaCandidatura;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.CandidaturaDuplicadaException;
import com.limio.api.modulos.anuncio.excecao.EscolhaConflitoException;

/** Camada técnica de persistência de {@link Aplicacao} — CRUD genérico (herdado) + finders da entity. */
@Component
public class AplicacaoService extends BaseService<Aplicacao, UUID, AplicacaoRepository> {

    private static final List<StatusAplicacao> STATUS_ABERTOS =
            List.of(StatusAplicacao.ENVIADA, StatusAplicacao.ESCOLHIDA, StatusAplicacao.AGUARDANDO_VALIDACAO);

    /** Derivado de {@link StatusAplicacao#isEscolhida()} — fonte única, não duplica a lista aqui. */
    private static final List<StatusAplicacao> STATUS_ESCOLHIDA =
            Arrays.stream(StatusAplicacao.values()).filter(StatusAplicacao::isEscolhida).toList();

    /** O escolhido continua sendo o Prestador do serviço depois de concluído. */
    private static final List<StatusAplicacao> STATUS_DO_SERVICO = List.of(StatusAplicacao.ESCOLHIDA,
            StatusAplicacao.AGUARDANDO_VALIDACAO, StatusAplicacao.CONCLUIDA);

    /** Mais recente primeiro; {@code id} desempata pra paginação estável. */
    private static final Sort MAIS_RECENTES = Sort.by(Sort.Order.desc("dataAplicacao"), Sort.Order.desc("id"));

    private static final String CONSTRAINT_PAR_ATIVO_UNICO = "uk_aplicacao_par_ativo";
    private static final String CONSTRAINT_ESCOLHIDA_UNICA = "idx_aplicacao_escolhida_unica";

    public AplicacaoService(AplicacaoRepository repository) {
        super(repository);
    }

    /** Algum prestador já foi escolhido pro anúncio (ver {@link Aplicacao#isEscolhida()}). */
    public boolean existeEscolhida(UUID anuncioId) {
        return repository.existsByAnuncioIdAndStatusIn(anuncioId, STATUS_ESCOLHIDA);
    }

    /** Prestador escolhido para o anúncio, mesmo depois de concluído. Vazio enquanto ninguém foi escolhido. */
    public Optional<UUID> buscarPrestadorDoServico(UUID anuncioId) {
        return repository.findFirstByAnuncioIdAndStatusIn(anuncioId, STATUS_DO_SERVICO).map(Aplicacao::getPrestadorId);
    }

    /** {@code prestadorId} é o escolhido deste anúncio (ver {@link Aplicacao#isEscolhida()}). */
    public boolean foiEscolhido(UUID anuncioId, UUID prestadorId) {
        return repository.existsByAnuncioIdAndPrestadorIdAndStatusIn(anuncioId, prestadorId, STATUS_ESCOLHIDA);
    }

    /** Candidaturas ainda sem resposta do Empregador, da mais antiga pra mais nova. */
    public List<Aplicacao> buscarEnviadasDoAnuncio(UUID anuncioId) {
        return repository.findByAnuncioIdAndStatusOrderByDataAplicacaoAscIdAsc(anuncioId, StatusAplicacao.ENVIADA);
    }

    /**
     * Só o {@code anuncioId} da candidatura, sem carregá-la. Serve pra descobrir qual trava de anúncio tomar antes de
     * ler o estado. Tem que ser escalar de propósito: carregar a entidade antes da trava a deixaria gerenciada no
     * contexto de persistência, e o {@code FOR UPDATE} seguinte devolveria essa cópia velha em vez do estado já
     * commitado por outra transação.
     */
    public Optional<UUID> buscarAnuncioId(UUID id) {
        return repository.findAnuncioIdById(id);
    }

    /** Busca que não lança: quem consulta por fora do módulo ({@code ConsultaCandidatura}) decide o que fazer. */
    public Optional<Aplicacao> buscarPorId(UUID id) {
        return repository.findById(id);
    }

    /** Candidaturas ainda abertas (não recusadas/concluídas/encerradas) em que {@code usuarioId} é o prestador. */
    public List<Aplicacao> buscarAbertasDoPrestador(UUID usuarioId) {
        return repository.findByPrestadorIdAndStatusIn(usuarioId, STATUS_ABERTOS);
    }

    /**
     * Candidaturas de {@code prestadorId} escolhidas no período ({@code escolhidaEm} entre {@code inicio}, incluso, e
     * {@code fimExclusivo}), qualquer que seja o status atual, da escolha mais recente pra mais antiga.
     */
    public List<Aplicacao> buscarEscolhidasDoPrestadorEntre(UUID prestadorId, Instant inicio, Instant fimExclusivo) {
        return repository.findByPrestadorIdAndEscolhidaEmGreaterThanEqualAndEscolhidaEmLessThanOrderByEscolhidaEmDesc(
                prestadorId, inicio, fimExclusivo);
    }

    /**
     * Todas as candidaturas ainda abertas de um anúncio (inclui {@code
     * ENVIADA} e {@code AGUARDANDO_VALIDACAO}, não só {@code ESCOLHIDA}) —
     * pra fechar e avisar todo mundo quando o anúncio encerra.
     */
    public List<Aplicacao> buscarAbertasDoAnuncio(UUID anuncioId) {
        return repository.findByAnuncioIdAndStatusIn(anuncioId, STATUS_ABERTOS);
    }

    /** Mesma busca que {@link #buscarAbertasDoAnuncio}, em lote pra vários anúncios de uma vez (evita N+1). */
    public List<Aplicacao> buscarAbertasDosAnuncios(List<UUID> anuncioIds) {
        if (anuncioIds.isEmpty()) {
            return List.of();
        }
        return repository.findByAnuncioIdInAndStatusIn(anuncioIds, STATUS_ABERTOS);
    }

    /** Já existe candidatura ativa deste Prestador pra este anúncio — recusa de duplicata (ETI-75). */
    public boolean existeAbertaDoPar(UUID anuncioId, UUID prestadorId) {
        return repository.existsByAnuncioIdAndPrestadorIdAndStatusIn(anuncioId, prestadorId, STATUS_ABERTOS);
    }

    /** Quantas vezes {@code prestadorId} foi escolhido entre {@code inicio} (incluso) e {@code fimExclusivo}. */
    public long contarEscolhidasNoPeriodo(UUID prestadorId, Instant inicio, Instant fimExclusivo) {
        return repository.countByPrestadorIdAndEscolhidaEmGreaterThanEqualAndEscolhidaEmLessThan(prestadorId, inicio,
                fimExclusivo);
    }

    /**
     * Quantos serviços {@code prestadorId} já concluiu como prestador escolhido (histórico do perfil
     * público, ETI-105). Conta pelo {@code Anuncio} concluído, não por {@code Aplicacao.status}: nenhum
     * fluxo marca a candidatura como {@code CONCLUIDA} (ver {@code AplicacaoRepository}).
     */
    public long contarConcluidasDoPrestador(UUID prestadorId) {
        return repository.countConcluidosPorAnuncioDoPrestador(prestadorId);
    }

    /**
     * {@code salvar} de uma candidatura nova: a checagem de duplicata do usecase já cobre o caso normal,
     * mas clique duplo/corrida pode passar os dois pela checagem antes de qualquer insert — só o índice
     * único {@code uk_aplicacao_par_ativo} garante sob concorrência (ETI-75, mesmo idioma de
     * {@code EnderecoSalvoService.salvar}: {@code saveAndFlush} pra a violação estourar aqui, não só no
     * fim da transação).
     */
    public Aplicacao salvarCandidatura(Aplicacao aplicacao) {
        try {
            return repository.saveAndFlush(aplicacao);
        } catch (DataIntegrityViolationException e) {
            if (violaConstraint(e, CONSTRAINT_PAR_ATIVO_UNICO)) {
                throw new CandidaturaDuplicadaException();
            }
            throw e;
        }
    }

    /**
     * {@code salvar} da candidatura escolhida (ETI-69). Com o lock do anúncio o usecase já barra a 2ª escolha; o índice
     * único {@code idx_aplicacao_escolhida_unica} é a rede de segurança do banco, e o {@code saveAndFlush} faz a
     * violação estourar aqui (virando 409) e não só no commit.
     */
    public Aplicacao salvarEscolha(Aplicacao aplicacao) {
        try {
            return repository.saveAndFlush(aplicacao);
        } catch (DataIntegrityViolationException e) {
            if (violaConstraint(e, CONSTRAINT_ESCOLHIDA_UNICA)) {
                throw new EscolhaConflitoException();
            }
            throw e;
        }
    }

    /**
     * As candidaturas {@code ENVIADA} do anúncio com {@code SELECT ... FOR UPDATE}, por id crescente (ETI-69). É a 2ª
     * etapa da ordem do agregado, depois do advisory lock do anúncio. Exige transação aberta.
     */
    public List<Aplicacao> buscarEnviadasDoAnuncioParaAtualizar(UUID anuncioId) {
        return repository.findEnviadasDoAnuncioParaAtualizar(anuncioId);
    }

    private static boolean violaConstraint(Throwable erro, String constraint) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacao
                    && constraint.equalsIgnoreCase(violacao.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    /** Candidaturas do Prestador que caem na {@code aba} (ETI-77), mais recentes primeiro. */
    public Page<Aplicacao> buscarDoPrestadorPorAba(UUID prestadorId, AbaCandidatura aba, int pagina, int tamanho) {
        return repository.findByPrestadorIdAndStatusIn(prestadorId, aba.status(),
                PageRequest.of(pagina, tamanho, MAIS_RECENTES));
    }

    /**
     * Busca com {@code SELECT ... FOR UPDATE}: quem muda o status de uma candidatura segura a linha até o fim da
     * transação. É a 2ª etapa da ordem do agregado: o usecase já tomou o advisory lock {@code
     * EscopoTrava.ANUNCIO} (a escolha, ETI-69, usa o mesmo mecanismo) e só então trava e relê a linha. Sozinho
     * ele não basta: quem não o toma (por exemplo uma recusa que lê sem lock) não espera. Exige transação aberta.
     */
    public Optional<Aplicacao> buscarParaAtualizar(UUID id) {
        return repository.findByIdParaAtualizar(id);
    }

    /**
     * A candidatura de {@code prestadorId} ao anúncio, de qualquer status. O banco não impede duas para o mesmo
     * par (só uma ESCOLHIDA por anúncio); com mais de uma vale a mais recente.
     */
    public Optional<Aplicacao> buscarDoPrestadorNoAnuncio(UUID anuncioId, UUID prestadorId) {
        return repository.findFirstByAnuncioIdAndPrestadorIdOrderByDataAplicacaoDescIdDesc(anuncioId, prestadorId);
    }

    /**
     * A candidatura que foi escolhida pro anúncio, se alguma já foi — vale mesmo depois do serviço concluir ou
     * o anúncio cancelar (ETI-86): {@code escolhidaEm} é permanente, diferente do {@code status} atual.
     */
    public Optional<Aplicacao> buscarEscolhidaDoAnuncio(UUID anuncioId) {
        return repository.findFirstByAnuncioIdAndEscolhidaEmIsNotNull(anuncioId);
    }
}
