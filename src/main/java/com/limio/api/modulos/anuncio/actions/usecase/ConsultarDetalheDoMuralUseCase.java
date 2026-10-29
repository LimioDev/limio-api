package com.limio.api.modulos.anuncio.actions.usecase;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.LinkTemporario;
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
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.DetalheDoMural;
import com.limio.api.modulos.anuncio.FotoDoAnuncio;
import com.limio.api.modulos.anuncio.actions.helper.ContatosHelper;
import com.limio.api.modulos.anuncio.actions.helper.DistanciaHelper;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.excecao.AnuncioNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.ContatosNaoConfirmadosException;
import com.limio.api.modulos.anuncio.excecao.PerfilIncompletoException;
import com.limio.api.modulos.anuncio.excecao.SomentePrestadorVeMuralException;

/**
 * UC "Ver detalhe do anúncio" do mural (US19). Mesmas pré-condições do mural (papel, contatos,
 * perfil completo — o perfil também dá a base pra distância).
 *
 * <p>Visibilidade: o anúncio aparece se está {@code PUBLICADO}, sem ninguém escolhido e não é do próprio Prestador;
 * quem já tem candidatura nele continua vendo o anúncio em qualquer estado, pra acompanhar a situação. Não exige
 * casar especialidade nem raio: quem abre um link recebido não deve levar 404 por isso. Qualquer outro caso é
 * 404, igual anúncio inexistente, pra não revelar que ele existe.
 *
 * <p>Nunca devolve endereço completo, contato nem coordenadas: só bairro, cidade, UF e a distância.
 */
@Service
public class ConsultarDetalheDoMuralUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConsultarDetalheDoMuralUseCase.class);

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaPerfilPrestador consultaPerfilPrestador;
    private final ConsultaCategoria consultaCategoria;
    private final ConsultaPerfilEmpregador consultaPerfilEmpregador;
    private final MidiaService midiaService;
    private final ArmazenamentoService armazenamentoService;

    public ConsultarDetalheDoMuralUseCase(AnuncioService anuncioService,
            AplicacaoService aplicacaoService,
            ConsultaDadosUsuario consultaDadosUsuario,
            ConsultaPerfilPrestador consultaPerfilPrestador,
            ConsultaCategoria consultaCategoria,
            ConsultaPerfilEmpregador consultaPerfilEmpregador,
            MidiaService midiaService,
            ArmazenamentoService armazenamentoService) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaPerfilPrestador = consultaPerfilPrestador;
        this.consultaCategoria = consultaCategoria;
        this.consultaPerfilEmpregador = consultaPerfilEmpregador;
        this.midiaService = midiaService;
        this.armazenamentoService = armazenamentoService;
    }

    @Transactional(readOnly = true)
    public DetalheDoMural executar(UUID usuarioId, PapelUsuario papelAtivo, UUID anuncioId) {
        exigirPrestador(papelAtivo);
        exigirContatosConfirmados(usuarioId);
        ResumoPerfilPrestador perfil = consultaPerfilPrestador.buscarCompleto(usuarioId)
                .orElseThrow(PerfilIncompletoException::new);

        Anuncio anuncio = anuncioService.buscarPorId(anuncioId).orElseThrow(AnuncioNaoEncontradoException::new);
        Optional<Aplicacao> minha = aplicacaoService.buscarDoPrestadorNoAnuncio(anuncioId, usuarioId);
        if (!visivel(anuncio, usuarioId, minha.isPresent())) {
            throw new AnuncioNaoEncontradoException();
        }

        ResumoCategoria categoria = consultaCategoria.buscarPorId(anuncio.getCategoriaId())
                .orElseThrow(() -> new IllegalStateException("categoria do anúncio não existe: " + anuncioId));
        ResumoPerfilEmpregador empregador = Optional
                .ofNullable(consultaPerfilEmpregador.buscarResumosEmLote(Set.of(anuncio.getContratanteId()))
                        .get(anuncio.getContratanteId()))
                .orElseThrow(() -> new IllegalStateException("empregador do anúncio não existe: " + anuncioId));
        List<FotoDoAnuncio> fotos = midiaService.buscarFotosAtivasDoAnuncio(anuncioId).stream()
                .map(foto -> new FotoDoAnuncio(foto.getId(), link(foto.getUrlS3()).orElse(null)))
                .toList();

        return new DetalheDoMural(anuncio.getId(), anuncio.getTitulo(), anuncio.getDescricao(), categoria, fotos,
                anuncio.getDataHoraPretendida(), anuncio.getBairroRegiaoSnapshot(), anuncio.getCidadeSnapshot(),
                anuncio.getUfSnapshot(),
                DistanciaHelper.haversineKm(perfil.latitudeBase(), perfil.longitudeBase(),
                        anuncio.getLatitudeSnapshot(), anuncio.getLongitudeSnapshot()),
                empregador, minha.map(Aplicacao::getStatus).orElse(null));
    }

    private boolean visivel(Anuncio anuncio, UUID usuarioId, boolean jaCandidatou) {
        if (anuncio.pertenceA(usuarioId)) {
            return false;
        }
        if (jaCandidatou) {
            return true;
        }
        return anuncio.getStatus() == StatusAnuncio.PUBLICADO && !aplicacaoService.existeEscolhida(anuncio.getId());
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

    private Optional<LinkTemporario> link(String chave) {
        try {
            return Optional.of(armazenamentoService.gerarLink(chave));
        } catch (ArmazenamentoIndisponivelException e) {
            log.warn("link de mídia indisponível; detalhe do mural segue sem a imagem");
            return Optional.empty();
        }
    }
}
