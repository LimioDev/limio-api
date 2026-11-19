package com.limio.api.modulos.anuncio.actions.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.CancelamentoServico;
import com.limio.api.modulos.anuncio.records.CancelamentoServicoResponse;
import com.limio.api.modulos.anuncio.FotoDoCheckout;
import com.limio.api.modulos.anuncio.ValidacaoDoServico;
import com.limio.api.modulos.anuncio.records.CheckoutResponse;
import com.limio.api.modulos.anuncio.records.ConsultaValidacaoResponse;
import com.limio.api.modulos.anuncio.records.FotoCheckoutResponse;
import com.limio.api.modulos.anuncio.records.ServicoIniciadoResponse;
import com.limio.api.modulos.anuncio.records.ServicoValidadoResponse;

@Component
public class ServicoMapper {

    public ServicoIniciadoResponse toIniciadoResponse(Anuncio anuncio) {
        return new ServicoIniciadoResponse(anuncio.getStatus(), anuncio.getIniciadoEm());
    }

    public ServicoValidadoResponse toValidadoResponse(Anuncio anuncio) {
        return new ServicoValidadoResponse(anuncio.getStatus(), anuncio.getValidadoEm());
    }

    public ConsultaValidacaoResponse toConsultaValidacaoResponse(ValidacaoDoServico validacao) {
        List<FotoCheckoutResponse> fotos = validacao.fotosCheckout().stream().map(ServicoMapper::toResponse).toList();
        return new ConsultaValidacaoResponse(fotos, validacao.anuncio().getCheckoutEm(), validacao.nomePrestador());
    }

    private static FotoCheckoutResponse toResponse(FotoDoCheckout foto) {
        LinkTemporario link = foto.link();
        return new FotoCheckoutResponse(foto.id(), link == null ? null : link.url(),
                link == null ? null : link.expiraEm());
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
