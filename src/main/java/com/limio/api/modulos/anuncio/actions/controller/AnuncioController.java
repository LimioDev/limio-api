package com.limio.api.modulos.anuncio.actions.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.actions.mapper.AnuncioMapper;
import com.limio.api.modulos.anuncio.actions.usecase.AnexarFotosAnuncioUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.ConsultarMeuAnuncioUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.EditarAnuncioUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.PublicarAnuncioUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.RemoverFotoAnuncioUseCase;
import com.limio.api.modulos.anuncio.records.CriarAnuncioRequest;
import com.limio.api.modulos.anuncio.records.CriarAnuncioResponse;
import com.limio.api.modulos.anuncio.records.EditarAnuncioRequest;
import com.limio.api.modulos.anuncio.records.FotoAnuncioResponse;
import com.limio.api.modulos.anuncio.records.MeuAnuncioResponse;

import jakarta.validation.Valid;

/**
 * Criar e publicar anúncio, anexar fotos e consultar o próprio anúncio (ETI-59) e editá-lo e remover fotos antes da escolha (ETI-61). Só o dono anexa, consulta, edita e remove:
 * anúncio de outra pessoa responde 404. O anúncio visto do mural (sem endereço completo) vai ter rota própria.
 */
@RestController
@RequestMapping("/anuncios")
public class AnuncioController {

    private final PublicarAnuncioUseCase publicarAnuncioUseCase;
    private final AnexarFotosAnuncioUseCase anexarFotosAnuncioUseCase;
    private final ConsultarMeuAnuncioUseCase consultarMeuAnuncioUseCase;
    private final EditarAnuncioUseCase editarAnuncioUseCase;
    private final RemoverFotoAnuncioUseCase removerFotoAnuncioUseCase;
    private final AnuncioMapper anuncioMapper;

    public AnuncioController(PublicarAnuncioUseCase publicarAnuncioUseCase,
            AnexarFotosAnuncioUseCase anexarFotosAnuncioUseCase,
            ConsultarMeuAnuncioUseCase consultarMeuAnuncioUseCase,
            EditarAnuncioUseCase editarAnuncioUseCase,
            RemoverFotoAnuncioUseCase removerFotoAnuncioUseCase,
            AnuncioMapper anuncioMapper) {
        this.publicarAnuncioUseCase = publicarAnuncioUseCase;
        this.anexarFotosAnuncioUseCase = anexarFotosAnuncioUseCase;
        this.consultarMeuAnuncioUseCase = consultarMeuAnuncioUseCase;
        this.editarAnuncioUseCase = editarAnuncioUseCase;
        this.removerFotoAnuncioUseCase = removerFotoAnuncioUseCase;
        this.anuncioMapper = anuncioMapper;
    }

    @PostMapping
    public ResponseEntity<CriarAnuncioResponse> criar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody CriarAnuncioRequest request) {
        Anuncio publicado = publicarAnuncioUseCase.executar(usuario.id(), usuario.papel(),
                anuncioMapper.toEntity(request), anuncioMapper.toEndereco(request));
        return ResponseEntity.created(URI.create("/anuncios/" + publicado.getId()))
                .body(anuncioMapper.toResponse(publicado));
    }

    @PostMapping(path = "/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<FotoAnuncioResponse>> anexarFotos(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID id, @RequestPart("arquivos") List<MultipartFile> arquivos) {
        List<byte[]> conteudos = arquivos.stream().map(AnuncioController::conteudo).toList();
        return ResponseEntity.ok(anuncioMapper.toResponse(
                anexarFotosAnuncioUseCase.executar(usuario.id(), id, conteudos)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeuAnuncioResponse> consultarMeu(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID id) {
        return ResponseEntity.ok(anuncioMapper.toResponse(consultarMeuAnuncioUseCase.executar(usuario.id(), id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MeuAnuncioResponse> editar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID id, @Valid @RequestBody EditarAnuncioRequest request) {
        editarAnuncioUseCase.executar(usuario.id(), id, anuncioMapper.toEntity(request));
        return ResponseEntity.ok(anuncioMapper.toResponse(consultarMeuAnuncioUseCase.executar(usuario.id(), id)));
    }

    @DeleteMapping("/{id}/fotos/{fotoId}")
    public ResponseEntity<Void> removerFoto(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID id, @PathVariable UUID fotoId) {
        removerFotoAnuncioUseCase.executar(usuario.id(), id, fotoId);
        return ResponseEntity.noContent().build();
    }

    private static byte[] conteudo(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler o arquivo enviado", e);
        }
    }
}
