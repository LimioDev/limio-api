package com.limio.api.modulos.anuncio.actions.usecase;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.MinhaCandidatura;
import com.limio.api.modulos.anuncio.actions.helper.NomeHelper;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.AbaCandidatura;

/**
 * Tela "Aplicações" do Prestador (ETI-77, US23): as candidaturas dele numa aba, paginadas, mais recentes
 * primeiro. Só lê candidaturas do próprio usuário — {@code prestadorId} é o id do token.
 *
 * Anúncios saem numa query só pra página inteira; o nome do Empregador é buscado uma vez por Empregador
 * distinto da página (a porta {@link ConsultaDadosUsuario} não tem busca em lote).
 */
@Service
public class ListarMinhasCandidaturasUseCase {

    static final int TAMANHO_PAGINA = 20;

    private static final Logger log = LoggerFactory.getLogger(ListarMinhasCandidaturasUseCase.class);

    private final AplicacaoService aplicacaoService;
    private final AnuncioService anuncioService;
    private final ConsultaDadosUsuario consultaDadosUsuario;

    public ListarMinhasCandidaturasUseCase(AplicacaoService aplicacaoService, AnuncioService anuncioService,
            ConsultaDadosUsuario consultaDadosUsuario) {
        this.aplicacaoService = aplicacaoService;
        this.anuncioService = anuncioService;
        this.consultaDadosUsuario = consultaDadosUsuario;
    }

    @Transactional(readOnly = true)
    public Page<MinhaCandidatura> executar(UUID prestadorId, AbaCandidatura aba, int pagina) {
        Page<Aplicacao> aplicacoes = aplicacaoService.buscarDoPrestadorPorAba(prestadorId, aba, pagina,
                TAMANHO_PAGINA);
        Map<UUID, Anuncio> anuncios = anuncioService
                .buscarPorIds(aplicacoes.stream().map(Aplicacao::getAnuncioId).distinct().toList())
                .stream()
                .collect(toMap(Anuncio::getId, identity()));
        Map<UUID, String> nomesDosEmpregadores = nomesCompletos(anuncios.values());

        List<MinhaCandidatura> itens = aplicacoes.stream()
                .map(aplicacao -> montar(aplicacao, anuncios, nomesDosEmpregadores))
                .flatMap(Optional::stream)
                .toList();
        return new PageImpl<>(itens, aplicacoes.getPageable(), aplicacoes.getTotalElements());
    }

    /**
     * Uma consulta por Empregador distinto. Conta encerrada fica fora do mapa (contrato de {@link
     * ConsultaDadosUsuario}) e o item sai sem nome.
     */
    private Map<UUID, String> nomesCompletos(Collection<Anuncio> anuncios) {
        Map<UUID, String> nomes = new HashMap<>();
        anuncios.stream().map(Anuncio::getContratanteId).distinct()
                .forEach(empregadorId -> consultaDadosUsuario.buscarPorId(empregadorId)
                        .ifPresent(dados -> nomes.put(empregadorId, dados.nomeCompleto())));
        return nomes;
    }

    /**
     * Vazio só se o anúncio sumiu: {@code anuncio_id} não tem FK (ADR-0001 §1.6) e anúncio nunca é apagado
     * (só encerrado), então isso é dado inconsistente — loga e segue com os outros itens. O total da página
     * continua contando o item pulado.
     */
    private Optional<MinhaCandidatura> montar(Aplicacao aplicacao, Map<UUID, Anuncio> anuncios,
            Map<UUID, String> nomesDosEmpregadores) {
        Anuncio anuncio = anuncios.get(aplicacao.getAnuncioId());
        if (anuncio == null) {
            log.warn("candidatura {} aponta pra anúncio inexistente; fora da lista", aplicacao.getId());
            return Optional.empty();
        }
        Optional<String> nome = Optional.ofNullable(nomesDosEmpregadores.get(anuncio.getContratanteId()));
        return Optional.of(new MinhaCandidatura(aplicacao, anuncio,
                nome.map(NomeHelper::abreviar).orElse(null),
                nome.map(NomeHelper::iniciais).orElse(null)));
    }
}
