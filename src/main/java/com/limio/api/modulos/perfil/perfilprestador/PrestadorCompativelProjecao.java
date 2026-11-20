package com.limio.api.modulos.perfil.perfilprestador;

import java.util.UUID;

/** Linha de {@link PerfilPrestadorRepository#buscarCompativeis}: usuário Prestador e distância em km (ETI-65). */
public interface PrestadorCompativelProjecao {

    UUID getUsuarioId();

    Double getDistanciaKm();
}
