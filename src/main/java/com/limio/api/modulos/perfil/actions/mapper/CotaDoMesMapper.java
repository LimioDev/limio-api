package com.limio.api.modulos.perfil.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.EscolhaDoPrestador;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaDoMes;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensal;
import com.limio.api.modulos.perfil.records.CotaDoMesResponse;
import com.limio.api.modulos.perfil.records.EscolhaDoMesResponse;

@Component
public class CotaDoMesMapper {

    public CotaDoMesResponse toResponse(CotaDoMes cotaDoMes) {
        CotaMensal cota = cotaDoMes.cota();
        return new CotaDoMesResponse(cotaDoMes.plano(), cota.getAnoMes().toString(), cota.getEscolhasConsumidas(),
                cotaDoMes.teto(), cotaDoMes.isEsgotada(), cota.renovaEm().atZone(CotaMensal.FUSO).toOffsetDateTime(),
                cotaDoMes.escolhas().stream().map(CotaDoMesMapper::toResponse).toList());
    }

    private static EscolhaDoMesResponse toResponse(EscolhaDoPrestador escolha) {
        return new EscolhaDoMesResponse(escolha.anuncioId(), escolha.titulo(), escolha.dataHoraServico(),
                escolha.escolhidaEm(), escolha.status());
    }
}
