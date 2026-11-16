package com.limio.api.modulos.financeiro.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.financeiro.ConsultaGold;
import com.limio.api.modulos.financeiro.assinaturagold.AssinaturaGold;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamento;
import com.limio.api.modulos.financeiro.records.AssinarGoldResponse;
import com.limio.api.modulos.financeiro.records.CartaoSalvoResponse;
import com.limio.api.modulos.financeiro.records.ConsultarGoldResponse;
import com.limio.api.modulos.financeiro.records.TokenizarCartaoResponse;

@Component
public class GoldMapper {

    public ConsultarGoldResponse toResponse(ConsultaGold consulta) {
        CartaoSalvoResponse cartao = consulta.cartaoSalvo()
                .map(meio -> new CartaoSalvoResponse(meio.getBandeira(), meio.getUltimos4()))
                .orElse(null);
        return new ConsultarGoldResponse(consulta.plano().getCodigo(), consulta.plano().getNome(),
                consulta.plano().getPrecoCentavos(), consulta.plano().getMoeda(), consulta.plano().getCiclo(), cartao);
    }

    public AssinarGoldResponse toResponse(AssinaturaGold assinatura) {
        return new AssinarGoldResponse(assinatura.getStatus(), assinatura.getDataFimCiclo());
    }

    public TokenizarCartaoResponse toResponse(MeioPagamento meioPagamento) {
        return new TokenizarCartaoResponse(meioPagamento.getGatewayToken(), meioPagamento.getBandeira(),
                meioPagamento.getUltimos4());
    }
}
