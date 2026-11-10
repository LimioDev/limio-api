package com.limio.api.modulos.moderacao.actions.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.moderacao.actions.mapper.AvaliacaoMapper;
import com.limio.api.modulos.moderacao.actions.usecase.AvaliarUseCase;
import com.limio.api.modulos.moderacao.actions.usecase.ConsultarAvaliacaoPendenteUseCase;
import com.limio.api.modulos.moderacao.records.AvaliacaoPendenteResponse;
import com.limio.api.modulos.moderacao.records.AvaliacaoResponse;
import com.limio.api.modulos.moderacao.records.AvaliarRequest;

import jakarta.validation.Valid;

/**
 * Avaliar a outra parte (ETI-107). O "serviço" é o anúncio. Não há checagem de papel: quem decide o acesso é ser uma
 * das duas partes, e para qualquer outra pessoa a resposta é 404 {@code SERVICO_NAO_ENCONTRADO}. Pular a avaliação
 * não tem rota: o app só fecha a tela.
 */
@RestController
public class AvaliacaoController {

    private final ConsultarAvaliacaoPendenteUseCase consultarAvaliacaoPendenteUseCase;
    private final AvaliarUseCase avaliarUseCase;
    private final AvaliacaoMapper avaliacaoMapper;

    public AvaliacaoController(ConsultarAvaliacaoPendenteUseCase consultarAvaliacaoPendenteUseCase,
            AvaliarUseCase avaliarUseCase, AvaliacaoMapper avaliacaoMapper) {
        this.consultarAvaliacaoPendenteUseCase = consultarAvaliacaoPendenteUseCase;
        this.avaliarUseCase = avaliarUseCase;
        this.avaliacaoMapper = avaliacaoMapper;
    }

    @GetMapping("/anuncios/{anuncioId}/avaliacao-pendente")
    public ResponseEntity<AvaliacaoPendenteResponse> consultarPendente(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID anuncioId) {
        return ResponseEntity.ok(avaliacaoMapper.toResponse(
                consultarAvaliacaoPendenteUseCase.executar(anuncioId, usuario.id())));
    }

    @PostMapping("/anuncios/{anuncioId}/avaliacoes")
    public ResponseEntity<AvaliacaoResponse> avaliar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID anuncioId, @Valid @RequestBody AvaliarRequest request) {
        var avaliacao = avaliarUseCase.executar(anuncioId, usuario.id(), request.nota(), request.comentario());
        return ResponseEntity.status(HttpStatus.CREATED).body(avaliacaoMapper.toResponse(avaliacao));
    }
}
