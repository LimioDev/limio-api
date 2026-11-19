package com.limio.api.comum.portas;

import java.util.List;
import java.util.UUID;

/**
 * Porta de leitura dos Prestadores que atendem um anúncio, para módulos que não podem importar {@code modulos.perfil}
 * (hoje o {@code anuncio}, no aviso de anúncio publicado — ETI-65). Só consulta; quem implementa é o {@code perfil},
 * dono da atuação.
 *
 * <p>É a regra do mural (ETI-73, UC "Filtrar mural") vista do outro lado: lá, os anúncios de um Prestador; aqui, os
 * Prestadores de um anúncio. Compatível = perfil completo, categoria do anúncio nas especialidades (ou "Outros" no
 * anúncio, ou "Outros" no Prestador) e distância até a base menor ou igual ao raio de atendimento dele.
 *
 * <p>Não sabe de conta, papel nem dono do anúncio: quem chama filtra o que é dele.
 */
public interface ConsultaPrestadoresCompativeis {

    /**
     * @param categoriaId categoria do anúncio
     * @param latitude    latitude do retrato do endereço do anúncio
     * @param longitude   longitude do retrato do endereço do anúncio
     * @return compatíveis, do mais perto pro mais longe; vazio se ninguém atende
     */
    List<PrestadorCompativel> buscarCompativeis(UUID categoriaId, double latitude, double longitude);
}
