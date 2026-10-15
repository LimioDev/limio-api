package com.limio.api.modulos.perfil.actions.usecase;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.perfil.actions.service.GeocodificacaoService;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.categoria.CategoriaService;
import com.limio.api.modulos.perfil.excecao.CategoriaInvalidaException;
import com.limio.api.modulos.perfil.excecao.MunicipioNaoEncontradoException;
import com.limio.api.modulos.perfil.perfilprestador.Coordenadas;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;
import com.limio.api.modulos.perfil.perfilprestador.enums.Uf;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.JanelaAtendimento;

/**
 * Regra de negócio do TICKET-0054 (salvar perfil de atuação). Estado completo: grava o
 * {@link PerfilPrestador} (cria no primeiro salvamento), substitui especialidades e janelas, resolve as
 * coordenadas do município-base e recalcula {@code completo}. Não conhece HTTP nem record.
 *
 * O perfil é sempre o do usuário do token, qualquer que seja o papel ativo: a troca pra Prestador (UC09)
 * tende a exigir perfil completo, então quem ainda está como Empregador precisa conseguir preenchê-lo.
 */
@Service
public class SalvarPerfilAtuacaoUseCase {

    private final PerfilPrestadorService perfilPrestadorService;
    private final CategoriaService categoriaService;
    private final GeocodificacaoService geocodificacaoService;

    public SalvarPerfilAtuacaoUseCase(PerfilPrestadorService perfilPrestadorService,
            CategoriaService categoriaService,
            GeocodificacaoService geocodificacaoService) {
        this.perfilPrestadorService = perfilPrestadorService;
        this.categoriaService = categoriaService;
        this.geocodificacaoService = geocodificacaoService;
    }

    @Transactional
    public PerfilPrestador executar(UUID usuarioId, Set<UUID> especialidadeIds, int raioAtendimentoKm,
            String municipioBase, Uf ufBase, List<JanelaAtendimento> janelas) {
        Set<Categoria> especialidades = buscarEspecialidades(especialidadeIds);
        Coordenadas coordenadasBase = localizar(municipioBase, ufBase);

        // Lock só depois da geocodificação: um Nominatim lento não segura o salvamento concorrente do mesmo usuário.
        perfilPrestadorService.travarPorUsuario(usuarioId);
        PerfilPrestador perfil = perfilPrestadorService.buscarPorUsuario(usuarioId)
                .orElseGet(() -> PerfilPrestador.novo(usuarioId));
        perfil.atualizarAtuacao(especialidades, raioAtendimentoKm, municipioBase, ufBase, coordenadasBase, janelas);
        return perfilPrestadorService.salvar(perfil);
    }

    /** Só categoria existente e ativa; um id fora disso recusa a gravação inteira. Lista vazia é aceita (UC). */
    private Set<Categoria> buscarEspecialidades(Set<UUID> ids) {
        Set<Categoria> encontradas = categoriaService.buscarAtivasPorIds(ids);
        if (encontradas.size() != ids.size()) {
            throw new CategoriaInvalidaException();
        }
        return encontradas;
    }

    /**
     * Coordenadas já gravadas pra este município (por qualquer perfil, inclusive o próprio num novo
     * salvamento) dispensam o Nominatim: a política do servidor público pede cache e bloqueia cliente que
     * repete a mesma consulta.
     */
    private Coordenadas localizar(String municipio, Uf uf) {
        return perfilPrestadorService.buscarCoordenadasConhecidas(municipio, uf)
                .or(() -> geocodificacaoService.localizarMunicipio(municipio, uf))
                .orElseThrow(MunicipioNaoEncontradoException::new);
    }
}
