package com.limio.api.modulos.anuncio.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.CancelamentoServico;
import com.limio.api.modulos.anuncio.records.CancelamentoServicoResponse;
import com.limio.api.modulos.anuncio.records.CheckoutResponse;
import com.limio.api.modulos.anuncio.records.ServicoIniciadoResponse;

@Component
public class ServicoMapper {

    public ServicoIniciadoResponse toIniciadoResponse(Anuncio anuncio) {
        return new ServicoIniciadoResponse(anuncio.getStatus(), anuncio.getIniciadoEm());
    }

    public CheckoutResponse toCheckoutResponse(Anuncio anuncio) {
        return new CheckoutResponse(anuncio.getStatus(), anuncio.getCheckoutEm(),
                anuncio.prazoDeValidacao().orElse(null));
    }

    public CancelamentoServicoResponse toCancelamentoResponse(CancelamentoServico cancelamento) {
        Anuncio anuncio = cancelamento.anuncio();
        return new CancelamentoServicoResponse(anuncio.getId(), anuncio.getStatus(), anuncio.getTipoCancelamento(),
                cancelamento.cotaEstornada());
    }
}
