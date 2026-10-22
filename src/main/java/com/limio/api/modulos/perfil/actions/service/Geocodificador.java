package com.limio.api.modulos.perfil.actions.service;

import java.util.Optional;

import com.limio.api.modulos.perfil.enums.Uf;
import com.limio.api.modulos.perfil.perfilprestador.Coordenadas;

/**
 * Porta de geocodificação do módulo {@code perfil}: os use cases dependem desta interface, nunca do provedor
 * (hoje {@link NominatimGeocodificador}). Fica em {@code perfil} e não em {@code comum/portas} porque só o
 * perfil geocodifica (critério "2+ módulos hoje" do ADR-0001).
 */
public interface Geocodificador {

    /**
     * Centro do município na UF. Vazio quando o provedor responde mas não acha município com esse nome ali.
     *
     * @throws com.limio.api.modulos.perfil.excecao.GeocodificacaoIndisponivelException provedor fora do ar,
     *         timeout, resposta de erro ou resposta fora do formato esperado
     */
    Optional<Coordenadas> localizarMunicipio(String municipio, Uf uf);
}
