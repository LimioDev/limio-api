package com.limio.api.modulos.perfil.actions.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.localizacao.enums.Uf;
import com.limio.api.comum.portas.ConsultaEndereco;
import com.limio.api.comum.portas.EnderecoInformado;
import com.limio.api.comum.portas.EnderecoLocalizado;
import com.limio.api.modulos.perfil.endereco.EnderecoParaGeocodificar;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;
import com.limio.api.modulos.perfil.excecao.EnderecoNaoLocalizadoException;
import com.limio.api.modulos.perfil.geo.Coordenadas;

/**
 * Implementa {@link ConsultaEndereco} para a criação de anúncio ({@code modulos.anuncio}, ETI-59). Salvo e
 * avulso saem no mesmo formato de uma linha, então o retrato do anúncio não depende de onde veio o endereço.
 */
@Component
public class ConsultaEnderecoService implements ConsultaEndereco {

    private final EnderecoSalvoService enderecoSalvoService;
    private final Geocodificador geocodificador;

    public ConsultaEnderecoService(EnderecoSalvoService enderecoSalvoService, Geocodificador geocodificador) {
        this.enderecoSalvoService = enderecoSalvoService;
        this.geocodificador = geocodificador;
    }

    /** Coordenadas do próprio endereço salvo: já foram obtidas no servidor quando ele foi gravado. */
    @Override
    @Transactional(readOnly = true)
    public EnderecoLocalizado buscarSalvo(UUID usuarioId, UUID enderecoSalvoId) {
        EnderecoSalvo salvo = enderecoSalvoService.buscarEnderecoAtivoDoUsuarioOuFalhar(enderecoSalvoId, usuarioId);
        return new EnderecoLocalizado(salvo.getBairroRegiao(), salvo.getUf(),
                emUmaLinha(salvo.getRua(), salvo.getNumero(), salvo.getComplemento(), salvo.getBairroRegiao(),
                        salvo.getCidade(), salvo.getUf()),
                salvo.getLatitude(), salvo.getLongitude());
    }

    /** Sem transação: a chamada ao geocodificador não segura conexão do pool. */
    @Override
    public EnderecoLocalizado localizar(EnderecoInformado endereco) {
        Coordenadas coordenadas = geocodificador.localizarEndereco(new EnderecoParaGeocodificar(endereco.rua(),
                endereco.numero(), endereco.cidade(), endereco.uf()))
                .orElseThrow(EnderecoNaoLocalizadoException::new);
        return new EnderecoLocalizado(endereco.bairroRegiao(), endereco.uf(),
                emUmaLinha(endereco.rua(), endereco.numero(), endereco.complemento(), endereco.bairroRegiao(),
                        endereco.cidade(), endereco.uf()),
                coordenadas.latitude(), coordenadas.longitude());
    }

    /**
     * "Rua, número - complemento, bairro, cidade - UF". Com os tamanhos máximos dos campos (V19) cabe nos 255
     * caracteres de {@code anuncio.endereco_completo_snapshot}.
     */
    static String emUmaLinha(String rua, String numero, String complemento, String bairroRegiao, String cidade,
            Uf uf) {
        String ruaENumero = rua + ", " + numero + (complemento == null ? "" : " - " + complemento);
        return ruaENumero + ", " + bairroRegiao + ", " + cidade + " - " + uf.name();
    }
}
