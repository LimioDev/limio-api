package com.limio.api.modulos.notificacao.actions.usecase;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.eventos.AvisoDeAnuncioEvent;
import com.limio.api.comum.eventos.CandidaturaCriadaEvent;
import com.limio.api.comum.eventos.CandidaturaRecusadaEvent;
import com.limio.api.comum.eventos.CheckoutEnviadoEvent;
import com.limio.api.comum.eventos.NovoServicoNaAreaEvent;
import com.limio.api.comum.eventos.PrestadorEscolhidoEvent;
import com.limio.api.comum.eventos.ServicoConcluidoEvent;
import com.limio.api.comum.eventos.ServicoIniciadoEvent;
import com.limio.api.comum.portas.ConsultaCandidatura;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.texto.NomeHelper;
import com.limio.api.modulos.notificacao.Aviso;
import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.NotificacaoService;
import com.limio.api.modulos.notificacao.actions.helper.AvisoHelper;
import com.limio.api.modulos.notificacao.eventos.NotificacaoRegistradaEvent;

/**
 * UC "Enviar notificações respeitando as preferências" (ETI-112), primeira metade: transforma cada evento de aviso
 * dos outros módulos em {@link Notificacao} gravada pra lista do app (tela 43). Todo aviso entra na lista, saindo ou
 * não por algum canal. O envio pelos canais é a segunda metade ({@link EntregarNotificacaoUseCase}), disparada pelo
 * {@link NotificacaoRegistradaEvent} depois do commit.
 *
 * <p>Os outros módulos só publicam eventos; nenhum chama este módulo (ADR-0003). Cada ouvinte roda depois do commit
 * de quem publicou, em transação própria: falha aqui não desfaz a ação que gerou o aviso (a escolha continua valendo).
 *
 * <p>Fora desta entrega (decisão de 2026-11-16): {@code ServicoCanceladoEvent} (#38) e {@code AnuncioExpiradoEvent}
 * (#39) ganham ouvinte quando as PRs deles entrarem; reagendamento e cobrança GOLD ainda não publicam evento entre
 * módulos.
 */
@Service
public class RegistrarAvisosUseCase {

    private static final String PRESTADOR_SEM_NOME = "Um prestador";

    private final NotificacaoService notificacaoService;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaCandidatura consultaCandidatura;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public RegistrarAvisosUseCase(NotificacaoService notificacaoService, ConsultaDadosUsuario consultaDadosUsuario,
            ConsultaCandidatura consultaCandidatura, ApplicationEventPublisher eventos, Clock relogio) {
        this.notificacaoService = notificacaoService;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaCandidatura = consultaCandidatura;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAnunciarNovoServico(NovoServicoNaAreaEvent evento) {
        registrar(AvisoHelper.novoServicoNaArea(evento.destinatarioId(), evento.anuncioId(), evento.categoriaNome(),
                evento.bairroRegiao(), evento.distanciaKm()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCriarCandidatura(CandidaturaCriadaEvent evento) {
        registrar(AvisoHelper.novaCandidatura(evento.contratanteId(), evento.anuncioId(),
                nomeAbreviado(evento.prestadorId()), evento.anuncioTitulo()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRecusarCandidatura(CandidaturaRecusadaEvent evento) {
        registrar(AvisoHelper.candidaturaRecusada(evento.prestadorId(), evento.anuncioId(), evento.anuncioTitulo()));
    }

    /**
     * O escolhido e cada não escolhido (as candidaturas que a escolha encerrou), com os não escolhidos numa consulta
     * só. Todos na mesma transação de propósito: falha ao gravar é do banco e pegaria os outros também, e como o envio
     * só começa depois do commit, nenhum push sai pra um aviso desfeito.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoEscolherPrestador(PrestadorEscolhidoEvent evento) {
        List<Aviso> avisos = new ArrayList<>();
        avisos.add(AvisoHelper.prestadorEscolhido(evento.prestadorId(), evento.anuncioId(), evento.anuncioTitulo()));
        consultaCandidatura.buscarPrestadoresIds(evento.candidaturasNaoEscolhidasIds())
                .forEach(naoEscolhido -> avisos.add(
                        AvisoHelper.naoEscolhido(naoEscolhido, evento.anuncioId(), evento.anuncioTitulo())));
        avisos.forEach(this::registrar);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoIniciarServico(ServicoIniciadoEvent evento) {
        registrar(AvisoHelper.servicoIniciado(evento.contratanteId(), evento.anuncioId(),
                nomeAbreviado(evento.prestadorId()), evento.anuncioTitulo()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoEnviarCheckout(CheckoutEnviadoEvent evento) {
        registrar(AvisoHelper.checkoutEnviado(evento.contratanteId(), evento.anuncioId(),
                nomeAbreviado(evento.prestadorId()), evento.anuncioTitulo()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoConcluirServico(ServicoConcluidoEvent evento) {
        registrar(AvisoHelper.servicoConcluido(evento.contratanteId(), evento.anuncioId(), evento.anuncioTitulo()));
        registrar(AvisoHelper.servicoConcluido(evento.prestadorId(), evento.anuncioId(), evento.anuncioTitulo()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAvisarSobreAnuncio(AvisoDeAnuncioEvent evento) {
        registrar(AvisoHelper.avisoDeAnuncio(evento.destinatarioId(), evento.anuncioId(), evento.assunto()));
    }

    private void registrar(Aviso aviso) {
        Notificacao notificacao = notificacaoService.salvar(Notificacao.builder()
                .usuarioId(aviso.destinatarioId())
                .anuncioId(aviso.anuncioId())
                .tipo(aviso.tipo())
                .titulo(aviso.titulo())
                .corpo(aviso.corpo())
                .enviadaEm(relogio.instant())
                .build());
        eventos.publishEvent(new NotificacaoRegistradaEvent(notificacao.getId()));
    }

    /** Mesmo critério da lista de candidatos (ETI-67): "Rafael S.". Conta encerrada ou sumida vira texto neutro. */
    private String nomeAbreviado(UUID usuarioId) {
        return consultaDadosUsuario.buscarPorId(usuarioId)
                .map(DadosUsuario::nomeCompleto)
                .map(NomeHelper::abreviar)
                .orElse(PRESTADOR_SEM_NOME);
    }
}
