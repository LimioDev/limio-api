package com.limio.api.modulos.perfil.actions.controller;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.perfil.actions.mapper.FotoPerfilMapper;
import com.limio.api.modulos.perfil.actions.usecase.ConsultarFotoPerfilUseCase;
import com.limio.api.modulos.perfil.actions.usecase.TrocarFotoPerfilUseCase;
import com.limio.api.modulos.perfil.records.FotoPerfilResponse;

@RestController
@RequestMapping("/conta/foto-perfil")
public class FotoPerfilController {

    private final TrocarFotoPerfilUseCase trocarFotoPerfilUseCase;
    private final ConsultarFotoPerfilUseCase consultarFotoPerfilUseCase;
    private final FotoPerfilMapper fotoPerfilMapper;

    public FotoPerfilController(TrocarFotoPerfilUseCase trocarFotoPerfilUseCase,
            ConsultarFotoPerfilUseCase consultarFotoPerfilUseCase, FotoPerfilMapper fotoPerfilMapper) {
        this.trocarFotoPerfilUseCase = trocarFotoPerfilUseCase;
        this.consultarFotoPerfilUseCase = consultarFotoPerfilUseCase;
        this.fotoPerfilMapper = fotoPerfilMapper;
    }

    /** 200 com o link da foto atual; 204 se o usuário nunca enviou foto. */
    @GetMapping
    public ResponseEntity<FotoPerfilResponse> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return consultarFotoPerfilUseCase.executar(usuario.id())
                .map(link -> ResponseEntity.ok(fotoPerfilMapper.toResponse(link)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FotoPerfilResponse> trocar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(fotoPerfilMapper.toResponse(
                trocarFotoPerfilUseCase.executar(usuario.id(), conteudo(arquivo))));
    }

    private static byte[] conteudo(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler o arquivo enviado", e);
        }
    }
}
