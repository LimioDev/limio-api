package com.limio.api.modulos.moderacao.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.moderacao.denuncia.AlvoDenuncia;
import com.limio.api.modulos.moderacao.denuncia.Denuncia;
import com.limio.api.modulos.moderacao.records.DenunciaRegistradaResponse;
import com.limio.api.modulos.moderacao.records.RegistrarDenunciaRequest;

@Component
public class DenunciaMapper {

    public AlvoDenuncia toAlvo(RegistrarDenunciaRequest request) {
        return new AlvoDenuncia(request.denunciadoId(), request.anuncioId(), request.candidaturaId(),
                request.mensagemId());
    }

    public DenunciaRegistradaResponse toResponse(Denuncia denuncia) {
        return new DenunciaRegistradaResponse(
                denuncia.getId(),
                denuncia.getNumero(),
                denuncia.getStatusResolucao(),
                denuncia.getPrazoAnalise());
    }
}
