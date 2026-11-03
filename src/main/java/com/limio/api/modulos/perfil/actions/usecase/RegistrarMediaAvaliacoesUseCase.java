package com.limio.api.modulos.perfil.actions.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.AvaliacaoPublicadaEvent;
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
        switch (evento.papelAvaliado()) {
            case EMPREGADOR -> perfilEmpregadorService.garantirPerfilSeContaAtiva(evento.avaliadoId())
                    .ifPresentOrElse(perfil -> {
                        perfil.registrarAvaliacoes(evento.mediaAvaliacoes(), evento.qtdAvaliacoes());
                        perfilEmpregadorService.salvar(perfil);
                    }, () -> semPerfil(evento));
            case PRESTADOR -> perfilPrestadorService.buscarPorUsuario(evento.avaliadoId())
                    .ifPresentOrElse(perfil -> {
                        perfil.registrarAvaliacoes(evento.mediaAvaliacoes(), evento.qtdAvaliacoes());
                        perfilPrestadorService.salvar(perfil);
                    }, () -> semPerfil(evento));
        }
    }

    private static void semPerfil(AvaliacaoPublicadaEvent evento) {
        log.info("avaliado {} sem perfil de {}; média não gravada", evento.avaliadoId(), evento.papelAvaliado());
    }
}
