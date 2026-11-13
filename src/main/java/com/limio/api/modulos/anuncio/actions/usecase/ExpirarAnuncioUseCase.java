package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.AnuncioExpiradoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;

/**
 * UC22 / RF30 / RB09 (ETI-66) — expira UM anúncio vencido e sem nenhuma candidatura: {@code ENCERRADO}, sem mexer
 * em candidaturas e nem em cota (sem escolha nunca houve consumo; por isso este usecase não conhece a porta
 * de cota). Publica {@link AnuncioExpiradoEvent}, entregue só após o commit (ADR-0003) — o aviso ao Empregador é do
 * módulo {@code notificacao} (US56).
 *
 * É um bean à parte do job de propósito: a transação e o evento dependem de a chamada passar pelo proxy do Spring, e
 * uma chamada de dentro do mesmo bean não passa. Uma transação por anúncio: a falha de um não desfaz os demais.
 *
 * Idempotente: o filtro do job é só uma pré-seleção. A decisão é tomada aqui, sob a trava do anúncio, com o estado
 * atual — quem chegar depois de outra expiração, de um cancelamento ou de uma candidatura vê o anúncio como ele ficou
 * e sai sem efeito nem evento.
 */
@Service
public class ExpirarAnuncioUseCase {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;

    public ExpirarAnuncioUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ApplicationEventPublisher eventos, TravaTransacional trava) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.eventos = eventos;
        this.trava = trava;
    }

    @Transactional
    public boolean executar(UUID anuncioId, Instant agora) {
        // Lock antes de ler: candidatura, escolha e cancelamento travam o mesmo escopo (ver EscopoTrava.ANUNCIO).
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Anuncio anuncio = anuncioService.buscarPorId(anuncioId).orElse(null);
        if (anuncio == null || !anuncio.permiteExpiracao(aplicacaoService.existeAlguma(anuncioId), agora)) {
            return false;
        }

        anuncio.expirar(agora);
        anuncioService.salvar(anuncio);

        eventos.publishEvent(new AnuncioExpiradoEvent(anuncioId, anuncio.getContratanteId(), anuncio.getTitulo(), agora));
        return true;
    }
}
