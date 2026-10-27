package com.limio.api.modulos.anuncio.actions.usecase;

import static java.util.stream.Collectors.groupingBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.ResumoPerfilEmpregador;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioNoPainel;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.PainelEmpregador;
import com.limio.api.modulos.anuncio.actions.helper.NomeHelper;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

/**
 * Painel "Início" do Empregador (TICKET-0052): nome e foto, contadores e os anúncios abertos dele.
 * Só lê anúncios do próprio usuário — {@code contratanteId} é o id do usuário logado.
 */
@Service
public class ConsultarPainelEmpregadorUseCase {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ConsultaPerfilEmpregador consultaPerfilEmpregador;
    private final ConsultaDadosUsuario consultaDadosUsuario;

    public ConsultarPainelEmpregadorUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ConsultaPerfilEmpregador consultaPerfilEmpregador, ConsultaDadosUsuario consultaDadosUsuario) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.consultaPerfilEmpregador = consultaPerfilEmpregador;
        this.consultaDadosUsuario = consultaDadosUsuario;
    }

    /** Não é {@code readOnly}: o primeiro acesso cria o perfil de exibição (ver {@link ConsultaPerfilEmpregador}). */
    @Transactional
    public PainelEmpregador executar(UUID usuarioId) {
        ResumoPerfilEmpregador perfil = consultaPerfilEmpregador.buscarResumo(usuarioId);
        List<Anuncio> anuncios = anuncioService.buscarAbertosDoContratantePorData(usuarioId);
        Map<UUID, List<Aplicacao>> aplicacoesPorAnuncio = aplicacaoService
                .buscarAbertasDosAnuncios(anuncios.stream().map(Anuncio::getId).toList())
                .stream()
                .collect(groupingBy(Aplicacao::getAnuncioId));
        List<AnuncioNoPainel> itens = anuncios.stream()
                .map(anuncio -> noPainel(anuncio, aplicacoesPorAnuncio.getOrDefault(anuncio.getId(), List.of())))
                .toList();
        return new PainelEmpregador(perfil, itens);
    }

    private AnuncioNoPainel noPainel(Anuncio anuncio, List<Aplicacao> aplicacoes) {
        int candidatos = (int) aplicacoes.stream().filter(a -> a.getStatus() == StatusAplicacao.ENVIADA).count();
        return new AnuncioNoPainel(anuncio, candidatos, prestadorEscolhido(aplicacoes));
    }

    /** {@code null} sem escolhido ou se a conta do prestador já não existe (encerrada). */
    private String prestadorEscolhido(List<Aplicacao> aplicacoes) {
        return aplicacoes.stream()
                .filter(Aplicacao::isEscolhida)
                .findFirst()
                .flatMap(escolhida -> consultaDadosUsuario.buscarPorId(escolhida.getPrestadorId()))
                .map(DadosUsuario::nomeCompleto)
                .map(NomeHelper::abreviar)
                .orElse(null);
    }
}
