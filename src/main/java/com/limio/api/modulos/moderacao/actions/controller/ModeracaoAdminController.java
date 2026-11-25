package com.limio.api.modulos.moderacao.actions.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.records.PageResponse;
import com.limio.api.comum.seguranca.AdministradorAutenticado;
import com.limio.api.modulos.moderacao.actions.mapper.ModeracaoAdminMapper;
import com.limio.api.modulos.moderacao.actions.usecase.AnalisarDenunciaUseCase;
import com.limio.api.modulos.moderacao.actions.usecase.DecidirDenunciaUseCase;
import com.limio.api.modulos.moderacao.actions.usecase.ListarFilaDeDenunciasUseCase;
import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;
import com.limio.api.modulos.moderacao.enums.Severidade;
import com.limio.api.modulos.moderacao.records.AnaliseDenunciaResponse;
import com.limio.api.modulos.moderacao.records.DecidirDenunciaRequest;
import com.limio.api.modulos.moderacao.records.DenunciaDecididaResponse;
import com.limio.api.modulos.moderacao.records.FilaDenunciaItemResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Fila, análise e decisão de denúncias no painel web (ETI-116). Fica em {@code /admin/**}, que o
 * {@code SecurityConfig} libera só para o administrador: usuário do app recebe 403 {@code ACESSO_NEGADO}.
 */
@RestController
@RequestMapping("/admin/denuncias")
public class ModeracaoAdminController {

    private final ListarFilaDeDenunciasUseCase listarFilaDeDenunciasUseCase;
    private final AnalisarDenunciaUseCase analisarDenunciaUseCase;
    private final DecidirDenunciaUseCase decidirDenunciaUseCase;
    private final ModeracaoAdminMapper mapper;

    public ModeracaoAdminController(ListarFilaDeDenunciasUseCase listarFilaDeDenunciasUseCase,
            AnalisarDenunciaUseCase analisarDenunciaUseCase, DecidirDenunciaUseCase decidirDenunciaUseCase,
            ModeracaoAdminMapper mapper) {
        this.listarFilaDeDenunciasUseCase = listarFilaDeDenunciasUseCase;
        this.analisarDenunciaUseCase = analisarDenunciaUseCase;
        this.decidirDenunciaUseCase = decidirDenunciaUseCase;
        this.mapper = mapper;
    }

    @GetMapping
    public PageResponse<FilaDenunciaItemResponse> listarFila(
            @RequestParam(required = false) MotivoDenuncia motivo,
            @RequestParam(required = false) Severidade severidade,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int tamanho) {
        return mapper.toResponse(listarFilaDeDenunciasUseCase.executar(motivo, severidade, pagina, tamanho));
    }

    @GetMapping("/{id}")
    public AnaliseDenunciaResponse analisar(@PathVariable UUID id) {
        return mapper.toResponse(analisarDenunciaUseCase.executar(id));
    }

    @PostMapping("/{id}/decisao")
    public DenunciaDecididaResponse decidir(@AuthenticationPrincipal AdministradorAutenticado administrador,
            @PathVariable UUID id, @Valid @RequestBody DecidirDenunciaRequest request, HttpServletRequest http) {
        return mapper.toResponse(decidirDenunciaUseCase.executar(id, administrador.id(), mapper.toPedido(request),
                http.getRemoteAddr()));
    }
}
