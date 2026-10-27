package com.limio.api.modulos.perfil.perfilprestador;

import java.util.Optional;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.base.BaseService;
import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.perfil.geo.Coordenadas;

/** Camada técnica de persistência de {@link PerfilPrestador} — CRUD genérico (herdado) + finders da entity. */
@Component
public class PerfilPrestadorService extends BaseService<PerfilPrestador, UUID, PerfilPrestadorRepository> {

    private final TravaTransacional travaTransacional;

    public PerfilPrestadorService(PerfilPrestadorRepository repository, TravaTransacional travaTransacional) {
        super(repository);
        this.travaTransacional = travaTransacional;
    }

    /**
     * Com especialidades e janelas carregadas — o mapper lê as duas no controller, fora da transação. Duas
     * queries (especialidades no fetch, janelas no {@code initialize}) em vez de um join só, que duplicava
     * as janelas. Vazio é fluxo normal: o Prestador ainda não salvou o perfil.
     */
    @Transactional(readOnly = true)
    public Optional<PerfilPrestador> buscarPorUsuario(UUID usuarioId) {
        Optional<PerfilPrestador> perfil = repository.findByUsuarioId(usuarioId);
        perfil.ifPresent(encontrado -> Hibernate.initialize(encontrado.getJanelas()));
        return perfil;
    }

    /**
     * Coordenadas que algum perfil já gravou pra este município/UF (inclusive o do próprio Prestador, num
     * novo salvamento). Funciona como cache da geocodificação.
     */
    public Optional<Coordenadas> buscarCoordenadasConhecidas(String municipio, Uf uf) {
        return repository.findFirstByUfBaseAndMunicipioBaseIgnoreCase(uf, municipio)
                .map(PerfilPrestador::getCoordenadasBase);
    }

    /** Apaga o perfil de atuação (especialidades, janelas e localização) do usuário, se existir. */
    public void apagarDoUsuario(UUID usuarioId) {
        repository.findByUsuarioId(usuarioId).ifPresent(repository::delete);
    }

    /**
     * Serializa os salvamentos do mesmo usuário até o fim da transação de quem chama. Sem isso, dois
     * salvamentos simultâneos do primeiro perfil (ex.: toque duplo em "Salvar") tentavam os dois criar o
     * registro, e o segundo estourava {@code uk_perfil_prestador_usuario_id} como 500.
     */
    public void travarPorUsuario(UUID usuarioId) {
        travaTransacional.travar(EscopoTrava.PERFIL_PRESTADOR, usuarioId.toString());
    }
}
