package com.limio.api.modulos.notificacao.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Ligar o WhatsApp exige gravar o consentimento em {@code AceiteTermo}, que ainda não existe. Enquanto isso o
 * canal não pode ser ligado — evita flag {@code true} sem trilha auditável (LGPD).
 */
public class WhatsappConsentimentoIndisponivelException extends NegocioException {

    public WhatsappConsentimentoIndisponivelException() {
        super(CodigoErro.WHATSAPP_INDISPONIVEL, HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoErro.WHATSAPP_INDISPONIVEL.mensagemPadrao());
    }
}
