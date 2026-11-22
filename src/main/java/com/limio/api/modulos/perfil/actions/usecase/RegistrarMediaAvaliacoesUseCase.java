package com.limio.api.modulos.perfil.actions.usecase;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.AvaliacaoOcultadaEvent;
import com.limio.api.comum.eventos.AvaliacaoPublicadaEvent;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;

/**
 * Grava no perfil público a média e a quantidade de avaliações que {@code moderacao} recalculou (ETI-107), no perfil do
 * papel que o avaliado teve no serviço.
 *
 * Escuta na mesma transação da avaliação, não em {@code AFTER_COMMIT}: o card pede a média no perfil assim que a nota
 * é gravada, e as duas coisas entram ou saem juntas. Não há efeito externo aqui, só banco (ADR-0003 só exige
 * {@code AFTER_COMMIT} para e-mail e storage).
 */
@Service
public class RegistrarMediaAvaliacoesUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrarMediaAvaliacoesUseCase.class);

    private final PerfilEmpregadorService perfilEmpregadorService;
    private final PerfilPrestadorService perfilPrestadorService;

    public RegistrarMediaAvaliacoesUseCase(PerfilEmpregadorService perfilEmpregadorService,
            PerfilPrestadorService perfilPrestadorService) {
        this.perfilEmpregadorService = perfilEmpregadorService;
        this.perfilPrestadorService = perfilPrestadorService;
    }

    /**
     * Empregador sem perfil ganha um, como ao abrir a tela. Prestador sem perfil de atuação fica sem a média: o perfil
     * dele não existe sem os dados de atuação, e a próxima avaliação já leva o total certo. Conta encerrada não tem
     * perfil para mostrar.
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void aoPublicarAvaliacao(AvaliacaoPublicadaEvent evento) {
        registrar(evento.avaliadoId(), evento.papelAvaliado(), evento.mediaAvaliacoes(), evento.qtdAvaliacoes());
    }

    /** ETI-116: a moderação ocultou uma avaliação, e o total já vem sem ela. Mesma gravação da publicação. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void aoOcultarAvaliacao(AvaliacaoOcultadaEvent evento) {
        registrar(evento.avaliadoId(), evento.papelAvaliado(), evento.mediaAvaliacoes(), evento.qtdAvaliacoes());
    }

    private void registrar(UUID avaliadoId, PapelUsuario papel, BigDecimal media, int qtd) {
        switch (papel) {
            case EMPREGADOR -> perfilEmpregadorService.garantirPerfilSeContaAtiva(avaliadoId)
                    .ifPresentOrElse(perfil -> {
                        perfil.registrarAvaliacoes(media, qtd);
                        perfilEmpregadorService.salvar(perfil);
                    }, () -> semPerfil(avaliadoId, papel));
            case PRESTADOR -> perfilPrestadorService.buscarPorUsuario(avaliadoId)
                    .ifPresentOrElse(perfil -> {
                        perfil.registrarAvaliacoes(media, qtd);
                        perfilPrestadorService.salvar(perfil);
                    }, () -> semPerfil(avaliadoId, papel));
        }
    }

    private static void semPerfil(UUID avaliadoId, PapelUsuario papel) {
        log.info("avaliado {} sem perfil de {}; média não gravada", avaliadoId, papel);
    }
}
