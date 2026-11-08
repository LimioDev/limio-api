package com.limio.api.modulos.anuncio.actions.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.actions.mapper.ServicoMapper;
import com.limio.api.modulos.anuncio.actions.usecase.RealizarCheckoutUseCase;
import com.limio.api.modulos.anuncio.records.CheckoutResponse;

@RestController
@RequestMapping("/anuncios/{anuncioId}/checkout")
public class CheckoutController {

    private final RealizarCheckoutUseCase realizarCheckoutUseCase;
    private final ServicoMapper servicoMapper;

    public CheckoutController(RealizarCheckoutUseCase realizarCheckoutUseCase, ServicoMapper servicoMapper) {
        this.realizarCheckoutUseCase = realizarCheckoutUseCase;
        this.servicoMapper = servicoMapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CheckoutResponse> fazerCheckout(@PathVariable UUID anuncioId,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestPart(name = "arquivos", required = false) List<MultipartFile> arquivos,
            @RequestParam(name = "observacao", required = false) String observacao) {
        List<byte[]> conteudos = arquivos == null ? List.of()
                : arquivos.stream().map(CheckoutController::conteudo).toList();
        var anuncio = realizarCheckoutUseCase.executar(anuncioId, usuario.id(), conteudos, observacao);
        return ResponseEntity.ok(servicoMapper.toCheckoutResponse(anuncio));
    }

    private static byte[] conteudo(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler o arquivo enviado", e);
        }
    }
}
