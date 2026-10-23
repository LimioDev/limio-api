package com.limio.api.modulos.perfil.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.limio.api.modulos.perfil.actions.service.Geocodificador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;
import com.limio.api.modulos.perfil.excecao.EnderecoNaoLocalizadoException;
import com.limio.api.modulos.perfil.perfilprestador.Coordenadas;

/**
 * As coordenadas são obtidas no geocodificador antes de abrir a transação: a chamada externa (até o timeout
 * configurado) não segura conexão do pool nem o lock do perfil. Só lock + gravação ficam na transação.
 */
@Service
public class CriarEnderecoSalvoUseCase {

    private final PerfilEmpregadorService perfilEmpregadorService;
    private final EnderecoSalvoService enderecoSalvoService;
    private final Geocodificador geocodificador;
    private final TransactionOperations transacao;

    public CriarEnderecoSalvoUseCase(PerfilEmpregadorService perfilEmpregadorService,
            EnderecoSalvoService enderecoSalvoService,
            Geocodificador geocodificador,
            TransactionOperations transacao) {
        this.perfilEmpregadorService = perfilEmpregadorService;
        this.enderecoSalvoService = enderecoSalvoService;
        this.geocodificador = geocodificador;
        this.transacao = transacao;
    }

    public EnderecoSalvo executar(UUID usuarioId, EnderecoSalvo novo) {
        Coordenadas coordenadas = geocodificador.localizarEndereco(novo.paraGeocodificar())
                .orElseThrow(EnderecoNaoLocalizadoException::new);

        return transacao.execute(status -> {
            PerfilEmpregador perfil = perfilEmpregadorService.garantirPerfilParaAtualizar(usuarioId);
            if (novo.isPadrao()) {
                enderecoSalvoService.desmarcarEnderecoPadrao(perfil.getId());
            }
            novo.setPerfilEmpregadorId(perfil.getId());
            novo.definirCoordenadas(coordenadas);
            return enderecoSalvoService.salvar(novo);
        });
    }
}
