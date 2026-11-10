package com.limio.api.modulos.moderacao.avaliacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.moderacao.excecao.AvaliacaoDuplicadaException;

/** Camada técnica de persistência de {@link Avaliacao} — CRUD genérico (herdado) + finders da entity. */
@Component
public class AvaliacaoService extends BaseService<Avaliacao, UUID, AvaliacaoRepository> {

    private static final String CONSTRAINT_UMA_POR_AVALIADOR = "uk_avaliacao_anuncio_avaliador";

    private final TravaTransacional travaTransacional;

    public AvaliacaoService(AvaliacaoRepository repository, TravaTransacional travaTransacional) {
        super(repository);
        this.travaTransacional = travaTransacional;
    }

    public boolean jaAvaliou(UUID anuncioId, UUID avaliadorId) {
        return repository.existsByAnuncioIdAndAvaliadorId(anuncioId, avaliadorId);
    }

    /**
     * {@code salvar} de uma avaliação nova: a checagem do usecase cobre o caso normal, e o índice único
     * {@code uk_avaliacao_anuncio_avaliador} segura a corrida ({@code saveAndFlush} pra a violação estourar aqui, mesmo
     * idioma de {@code AplicacaoService.salvarCandidatura}).
     */
    public Avaliacao salvarNova(Avaliacao avaliacao) {
        try {
            return repository.saveAndFlush(avaliacao);
        } catch (DataIntegrityViolationException e) {
            if (violaUmaPorAvaliador(e)) {
                throw new AvaliacaoDuplicadaException();
            }
            throw e;
        }
    }

    /**
     * Serializa, até o fim da transação de quem chama, as avaliações que um mesmo usuário recebe num papel. Sem isso,
     * duas avaliações simultâneas calculavam a média cada uma sem a outra, e a última a gravar deixava o perfil atrás.
     */
    public void travarRecebidas(UUID avaliadoId, PapelUsuario papel) {
        travaTransacional.travar(EscopoTrava.AVALIACOES_RECEBIDAS, avaliadoId + ":" + papel);
    }

    /** Média com uma casa decimal (arredondamento comercial), do jeito que o perfil guarda. */
    public AvaliacoesRecebidas recalcular(UUID avaliadoId, PapelUsuario papel) {
        int qtd = Math.toIntExact(repository.countByAvaliadoIdAndPapelAvaliado(avaliadoId, papel));
        Double media = repository.mediaDasNotas(avaliadoId, papel);
        return new AvaliacoesRecebidas(
                media == null ? null : BigDecimal.valueOf(media).setScale(1, RoundingMode.HALF_UP), qtd);
    }

    private static boolean violaUmaPorAvaliador(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacao
                    && CONSTRAINT_UMA_POR_AVALIADOR.equalsIgnoreCase(violacao.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
