package com.limio.api.modulos.perfil.perfilprestador;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.perfil.perfilprestador.enums.Uf;

/** Camada técnica de persistência de {@link PerfilPrestador} — CRUD genérico (herdado) + finders da entity. */
@Component
public class PerfilPrestadorService extends BaseService<PerfilPrestador, UUID, PerfilPrestadorRepository> {

    public PerfilPrestadorService(PerfilPrestadorRepository repository) {
        super(repository);
    }

    /** Com especialidades e janelas carregadas. Vazio é fluxo normal: o Prestador ainda não salvou o perfil. */
    public Optional<PerfilPrestador> buscarPorUsuario(UUID usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }

    /**
     * Coordenadas que algum perfil já gravou pra este município/UF (inclusive o do próprio Prestador, num
     * novo salvamento). Funciona como cache da geocodificação.
     */
    public Optional<Coordenadas> buscarCoordenadasConhecidas(String municipio, Uf uf) {
        return repository.findFirstByUfBaseAndMunicipioBaseIgnoreCase(uf, municipio)
                .map(perfil -> new Coordenadas(perfil.getLatitudeBase(), perfil.getLongitudeBase()));
    }

    /**
     * Serializa os salvamentos do mesmo usuário até o fim da transação de quem chama. Sem isso, dois
     * salvamentos simultâneos do primeiro perfil (ex.: toque duplo em "Salvar") tentavam os dois criar o
     * registro, e o segundo estourava {@code uk_perfil_prestador_usuario_id} como 500.
     */
    public void travarPorUsuario(UUID usuarioId) {
        repository.travarPorUsuario(usuarioId.toString());
    }
}
