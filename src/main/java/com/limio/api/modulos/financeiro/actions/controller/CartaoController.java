package com.limio.api.modulos.financeiro.actions.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.financeiro.actions.mapper.GoldMapper;
import com.limio.api.modulos.financeiro.actions.usecase.TokenizarCartaoUseCase;
import com.limio.api.modulos.financeiro.records.TokenizarCartaoRequest;
import com.limio.api.modulos.financeiro.records.TokenizarCartaoResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Tokeniza o cartão no Asaas e salva como meio de pagamento do Prestador (ETI-81, integração Asaas).
 * Só existe com {@code financeiro.gateway.provedor=asaas} — não é parte do contrato original do card,
 * nasceu pra permitir testar "Assinar GOLD" de ponta a ponta contra o sandbox sem a tela de cadastro de
 * cartão (card separado, ainda sem ticket).
 */
@RestController
@RequestMapping("/financeiro/gold/cartao")
@ConditionalOnProperty(name = "financeiro.gateway.provedor", havingValue = "asaas")
public class CartaoController {

    private final TokenizarCartaoUseCase tokenizarCartaoUseCase;
    private final GoldMapper goldMapper;

    public CartaoController(TokenizarCartaoUseCase tokenizarCartaoUseCase, GoldMapper goldMapper) {
        this.tokenizarCartaoUseCase = tokenizarCartaoUseCase;
        this.goldMapper = goldMapper;
    }

    @PostMapping
    public ResponseEntity<TokenizarCartaoResponse> tokenizar(@Valid @RequestBody TokenizarCartaoRequest request,
            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest httpRequest) {
        var meioPagamento = tokenizarCartaoUseCase.executar(usuario.id(), usuario.papel(), request.holderName(),
                request.numero(), request.expiryMonth(), request.expiryYear(), request.ccv(), request.holderNome(),
                request.holderEmail(), request.holderCpf(), request.holderCep(), request.holderNumeroEndereco(),
                request.holderTelefone(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(goldMapper.toResponse(meioPagamento));
    }
}
