package com.limio.api.modulos.moderacao.actions.controller;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.moderacao.actions.mapper.DenunciaMapper;
import com.limio.api.modulos.moderacao.actions.usecase.RegistrarDenunciaUseCase;
import com.limio.api.modulos.moderacao.records.DenunciaRegistradaResponse;
import com.limio.api.modulos.moderacao.records.RegistrarDenunciaRequest;

import jakarta.validation.Valid;

/**
 * Registrar denúncia (ETI-114). {@code multipart/form-data}: os campos de {@link RegistrarDenunciaRequest} e a parte
 * {@code foto}, opcional. Sem checagem de papel: quem decide o acesso é o alvo (ver {@link RegistrarDenunciaUseCase}).
 */
@RestController
public class DenunciaController {

    private final RegistrarDenunciaUseCase registrarDenunciaUseCase;
    private final DenunciaMapper denunciaMapper;

    public DenunciaController(RegistrarDenunciaUseCase registrarDenunciaUseCase, DenunciaMapper denunciaMapper) {
        this.registrarDenunciaUseCase = registrarDenunciaUseCase;
        this.denunciaMapper = denunciaMapper;
    }

    @PostMapping(path = "/denuncias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DenunciaRegistradaResponse> registrar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @ModelAttribute RegistrarDenunciaRequest request,
            @RequestPart(name = "foto", required = false) MultipartFile foto) {
        var denuncia = registrarDenunciaUseCase.executar(usuario.id(), denunciaMapper.toAlvo(request),
                request.motivo(), request.descricao(), foto == null ? null : conteudo(foto));
        return ResponseEntity.status(HttpStatus.CREATED).body(denunciaMapper.toResponse(denuncia));
    }

    private static byte[] conteudo(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler o arquivo enviado", e);
        }
    }
}
