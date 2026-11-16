package com.limio.api.modulos.anuncio.actions.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.actions.mapper.ServicoMapper;
import com.limio.api.modulos.anuncio.actions.usecase.ConsultarValidacaoUseCase;
import com.limio.api.modulos.anuncio.records.ConsultaValidacaoResponse;

/** Consultar a validação antes de confirmar: fotos do check-out, quando foi feito e o nome do Prestador (ETI-92). */
@RestController
@RequestMapping("/anuncios/{anuncioId}/validacao")
public class ConsultarValidacaoController {

    private final ConsultarValidacaoUseCase consultarValidacaoUseCase;
    private final ServicoMapper servicoMapper;

    public ConsultarValidacaoController(ConsultarValidacaoUseCase consultarValidacaoUseCase,
            ServicoMapper servicoMapper) {
        this.consultarValidacaoUseCase = consultarValidacaoUseCase;
        this.servicoMapper = servicoMapper;
    }

    @GetMapping
    public ResponseEntity<ConsultaValidacaoResponse> consultar(@PathVariable UUID anuncioId,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(servicoMapper
                .toConsultaValidacaoResponse(consultarValidacaoUseCase.executar(anuncioId, usuario.id())));
    }
}
