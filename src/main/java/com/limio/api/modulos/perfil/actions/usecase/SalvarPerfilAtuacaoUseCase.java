package com.limio.api.modulos.perfil.actions.usecase;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.limio.api.modulos.perfil.actions.service.Geocodificador;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.categoria.CategoriaService;
import com.limio.api.modulos.perfil.enums.Uf;
import com.limio.api.modulos.perfil.excecao.CategoriaInvalidaException;
import com.limio.api.modulos.perfil.excecao.MunicipioNaoEncontradoException;
import com.limio.api.modulos.perfil.geo.Coordenadas;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.JanelaAtendimento;

/**
 * Regra de negócio do TICKET-0054 (salvar perfil de atuação). Estado completo: grava o
 * {@link PerfilPrestador} (cria no primeiro salvamento), substitui especialidades e janelas, resolve as
 * coordenadas do município-base e recalcula {@code completo}. Não conhece HTTP nem record.
 *
 * O perfil é sempre o do usuário do token, qualquer que seja o papel ativo: a troca pra Prestador (UC09)
 * tende a exigir perfil completo, então quem ainda está como Empregador precisa conseguir preenchê-lo.
 *
 * Validação das especialidades e geocodificação rodam antes de abrir a transação: a chamada ao Nominatim
 * (até o timeout configurado) não segura conexão do pool, e PUTs concorrentes pra municípios novos não
 * esgotam o pool travando endpoints sem relação. Só lock + leitura + gravação ficam na transação.
 */
@Service
public class SalvarPerfilAtuacaoUseCase {

    private final PerfilPrestadorService perfilPrestadorService;
    private final CategoriaService categoriaService;
    private final Geocodificador geocodificador;
    private final TransactionOperations transacao;

    public SalvarPerfilAtuacaoUseCase(PerfilPrestadorService perfilPrestadorService,
            CategoriaService categoriaService,
            Geocodificador geocodificador,
            TransactionOperations transacao) {
        this.perfilPrestadorService = perfilPrestadorService;
        this.categoriaService = categoriaService;
        this.geocodificador = geocodificador;
        this.transacao = transacao;
    }

    public PerfilPrestador executar(UUID usuarioId, Set<UUID> especialidadeIds, int raioAtendimentoKm,
            String municipioBase, Uf ufBase, List<JanelaAtendimento> janelas) {
        Set<Categoria> especialidades = buscarEspecialidades(especialidadeIds);
        Coordenadas coordenadasBase = localizar(municipioBase, ufBase);

        return transacao.execute(status -> {
            perfilPrestadorService.travarPorUsuario(usuarioId);
            PerfilPrestador perfil = perfilPrestadorService.buscarPorUsuario(usuarioId)
                    .orElseGet(() -> PerfilPrestador.novo(usuarioId));
            perfil.atualizarAtuacao(especialidades, raioAtendimentoKm, municipioBase, ufBase, coordenadasBase,
                    janelas);
            return perfilPrestadorService.salvar(perfil);
        });
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
                .or(() -> geocodificador.localizarMunicipio(municipio, uf))
                .orElseThrow(MunicipioNaoEncontradoException::new);
    }
}
