package com.limio.api.modulos.perfil.actions.usecase;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.FotoPerfilRemovidaPelaModeracaoEvent;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.modulos.perfil.eventos.FotoPerfilRemovidaEvent;

/**
 * UC "Enviar foto de perfil": o administrador remove a foto numa decisão de denúncia (ETI-116). Roda na transação da
 * decisão: a foto sai de uso junto com a decisão, e o objeto sai do storage só depois do commit
 * ({@link FotoPerfilRemovidaEvent}, como no encerramento de conta). Sem foto, nada a fazer. O usuário pode enviar
 * outra depois.
 */
@Service
public class RemoverFotoPerfilPelaModeracaoUseCase {

    private final MidiaService midiaService;
    private final ApplicationEventPublisher eventos;

    public RemoverFotoPerfilPelaModeracaoUseCase(MidiaService midiaService, ApplicationEventPublisher eventos) {
        this.midiaService = midiaService;
        this.eventos = eventos;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void aoRemoverPelaModeracao(FotoPerfilRemovidaPelaModeracaoEvent evento) {
        midiaService.travarFotoPerfil(evento.usuarioId());
        midiaService.buscarFotoPerfilAtiva(evento.usuarioId()).ifPresent(foto -> {
            foto.expurgar(evento.ocorridoEm());
            midiaService.salvar(foto);
            eventos.publishEvent(new FotoPerfilRemovidaEvent(foto.getUrlS3()));
        });
    }
}
