package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.ConsultaPerfilPrestador;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.comum.portas.ResumoPerfilPrestador;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.AnuncioDoMuralProjecao;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.CriterioDoMural;
import com.limio.api.modulos.anuncio.ItemDoMural;
import com.limio.api.modulos.anuncio.actions.helper.ContatosHelper;
import com.limio.api.modulos.anuncio.actions.helper.JanelaDoMuralHelper;
import com.limio.api.modulos.anuncio.actions.helper.JanelaDoMuralHelper.Janela;
import com.limio.api.modulos.anuncio.enums.QuandoMural;
import com.limio.api.modulos.anuncio.excecao.ContatosNaoConfirmadosException;
import com.limio.api.modulos.anuncio.excecao.PerfilIncompletoException;
import com.limio.api.modulos.anuncio.excecao.SomentePrestadorVeMuralException;

/**
 * UC "Ver o mural de anúncios" (US19) com os filtros da US20: só anúncios compatíveis com o
 * Prestador, sem dado de contato.
 *
 * <p>Compatibilidade fixa (a consulta aplica no banco): categoria do anúncio nas especialidades, ou
 * "Outros" em qualquer dos lados, e distância até a base do Prestador dentro do raio dele. Os filtros da tela
 * só reduzem esse conjunto: o {@code raio} nunca passa do raio do Prestador, as {@code categorias} se
 * intersectam com a compatibilidade e o {@code quando} só recorta datas — as janelas de atuação do Prestador
 * não entram. Plano (FREE/GOLD) não existe aqui: todos veem o mesmo.
 */
@Service
public class ListarMuralUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListarMuralUseCase.class);

    private final AnuncioService anuncioService;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaPerfilPrestador consultaPerfilPrestador;
    private final ConsultaCategoria consultaCategoria;
    private final ConsultaPerfilEmpregador consultaPerfilEmpregador;
    private final MidiaService midiaService;
    private final ArmazenamentoService armazenamentoService;

    public ListarMuralUseCase(AnuncioService anuncioService,
            ConsultaDadosUsuario consultaDadosUsuario,
            ConsultaPerfilPrestador consultaPerfilPrestador,
            ConsultaCategoria consultaCategoria,
            ConsultaPerfilEmpregador consultaPerfilEmpregador,
            MidiaService midiaService,
            ArmazenamentoService armazenamentoService) {
        this.anuncioService = anuncioService;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaPerfilPrestador = consultaPerfilPrestador;
        this.consultaCategoria = consultaCategoria;
        this.consultaPerfilEmpregador = consultaPerfilEmpregador;
        this.midiaService = midiaService;
        this.armazenamentoService = armazenamentoService;
    }

    /**
     * @param categorias filtro de categorias; nulo ou vazio = sem filtro
     * @param raioKm filtro de raio; nulo = sem filtro (vale o raio do Prestador)
     */
    @Transactional(readOnly = true)
    public Page<ItemDoMural> executar(UUID usuarioId, PapelUsuario papelAtivo, Set<UUID> categorias, Integer raioKm,
            QuandoMural quando, int pagina, int tamanho) {
        exigirPrestador(papelAtivo);
        exigirContatosConfirmados(usuarioId);
        ResumoPerfilPrestador perfil = consultaPerfilPrestador.buscarCompleto(usuarioId)
                .orElseThrow(PerfilIncompletoException::new);

        Janela janela = JanelaDoMuralHelper.de(quando, Instant.now());
        int raioEfetivo = raioKm == null ? perfil.raioAtendimentoKm() : Math.min(raioKm, perfil.raioAtendimentoKm());
        CriterioDoMural criterio = new CriterioDoMural(usuarioId, perfil.especialidadeIds(), perfil.categoriaOutrosId(),
                perfil.latitudeBase(), perfil.longitudeBase(), raioEfetivo,
                categorias == null ? Set.of() : categorias, janela.de(), janela.ate());

        Page<AnuncioDoMuralProjecao> linhas = anuncioService.buscarMural(criterio, PageRequest.of(pagina, tamanho));
        return linhas.map(montar(linhas.getContent()));
    }

    private Function<AnuncioDoMuralProjecao, ItemDoMural> montar(List<AnuncioDoMuralProjecao> linhas) {
        if (linhas.isEmpty()) {
            return linha -> null;
        }
        Set<UUID> anuncioIds = new HashSet<>();
        Set<UUID> categoriaIds = new HashSet<>();
        Set<UUID> empregadorIds = new HashSet<>();
        linhas.forEach(linha -> {
            anuncioIds.add(linha.getId());
            categoriaIds.add(linha.getCategoriaId());
            empregadorIds.add(linha.getContratanteId());
        });
        Map<UUID, ResumoCategoria> categorias = consultaCategoria.buscarPorIdsEmLote(categoriaIds);
        Map<UUID, ResumoPerfilEmpregador> empregadores = consultaPerfilEmpregador.buscarResumosEmLote(empregadorIds);
        Map<UUID, Midia> fotos = midiaService.buscarPrimeirasFotosDosAnuncios(anuncioIds);

        return linha -> new ItemDoMural(
                linha.getId(),
                linha.getTitulo(),
                Optional.ofNullable(categorias.get(linha.getCategoriaId())).orElseThrow(() ->
                        new IllegalStateException("categoria do anúncio não existe: " + linha.getId())),
                Optional.ofNullable(fotos.get(linha.getId())).flatMap(this::link).orElse(null),
                Optional.ofNullable(empregadores.get(linha.getContratanteId())).orElseThrow(() ->
                        new IllegalStateException("empregador do anúncio não existe: " + linha.getId())),
                linha.getDataHoraPretendida(),
                linha.getBairroRegiao(),
                linha.getDistanciaKm());
    }

    private static void exigirPrestador(PapelUsuario papelAtivo) {
        switch (papelAtivo) {
            case PRESTADOR -> {
            }
            case EMPREGADOR -> throw new SomentePrestadorVeMuralException();
        }
    }

    private void exigirContatosConfirmados(UUID usuarioId) {
        boolean confirmados = consultaDadosUsuario.buscarPorId(usuarioId)
                .map(ContatosHelper::confirmados)
                .orElse(false);
        if (!confirmados) {
            throw new ContatosNaoConfirmadosException("Confirme seu e-mail e seu telefone para ver os anúncios.");
        }
    }

    private Optional<LinkTemporario> link(Midia foto) {
        try {
            return Optional.of(armazenamentoService.gerarLink(foto.getUrlS3()));
        } catch (ArmazenamentoIndisponivelException e) {
            log.warn("link de mídia indisponível; mural segue sem a foto do anúncio");
            return Optional.empty();
        }
    }
}
