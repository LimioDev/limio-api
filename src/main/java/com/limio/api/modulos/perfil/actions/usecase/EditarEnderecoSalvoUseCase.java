package com.limio.api.modulos.perfil.actions.usecase;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.limio.api.modulos.perfil.actions.service.Geocodificador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;
import com.limio.api.modulos.perfil.excecao.EnderecoNaoLocalizadoException;
import com.limio.api.modulos.perfil.geo.Coordenadas;

/**
 * Só consulta o geocodificador quando rua, número, cidade ou UF mudam; editar apelido, CEP, bairro,
 * complemento ou padrão mantém as coordenadas gravadas (poupa o limite do provedor). {@code padrao} nulo
 * mantém o padrão como está; só um valor explícito o altera. A consulta roda fora da
 * transação. Editar nunca cria perfil: sem perfil é 404 (o endereço não existiria), e a leitura fora da
 * transação não tem efeito colateral. Risco aceito: duas edições simultâneas do mesmo endereço pelo mesmo dono podem misturar o texto
 * de uma com as coordenadas da outra.
 */
@Service
public class EditarEnderecoSalvoUseCase {

    private final PerfilEmpregadorService perfilEmpregadorService;
    private final EnderecoSalvoService enderecoSalvoService;
    private final Geocodificador geocodificador;
    private final TransactionOperations transacao;

    public EditarEnderecoSalvoUseCase(PerfilEmpregadorService perfilEmpregadorService,
            EnderecoSalvoService enderecoSalvoService,
            Geocodificador geocodificador,
            TransactionOperations transacao) {
        this.perfilEmpregadorService = perfilEmpregadorService;
        this.enderecoSalvoService = enderecoSalvoService;
        this.geocodificador = geocodificador;
        this.transacao = transacao;
    }

    public EnderecoSalvo executar(UUID usuarioId, UUID enderecoId, EnderecoSalvo novosDados, Boolean padrao) {
        UUID perfilId = perfilEmpregadorService.buscarPerfilExistente(usuarioId).getId();
        EnderecoSalvo atual = enderecoSalvoService.buscarEnderecoAtivoOuFalhar(enderecoId, perfilId);
        Optional<Coordenadas> novas = atual.mesmaLocalizacaoQue(novosDados)
                ? Optional.empty()
                : Optional.of(geocodificador.localizarEndereco(novosDados.paraGeocodificar())
                        .orElseThrow(EnderecoNaoLocalizadoException::new));

        return transacao.execute(status -> {
            PerfilEmpregador perfil = perfilEmpregadorService.travarPerfilExistente(perfilId);
            if (Boolean.TRUE.equals(padrao)) {
                enderecoSalvoService.desmarcarEnderecoPadrao(perfil.getId());
            }
            EnderecoSalvo endereco = enderecoSalvoService.buscarEnderecoAtivoOuFalhar(enderecoId, perfil.getId());
            endereco.substituirDadosPor(novosDados);
            if (Boolean.TRUE.equals(padrao)) {
                endereco.marcarComoPadrao();
            } else if (Boolean.FALSE.equals(padrao)) {
                endereco.desmarcarPadrao();
            }
            novas.ifPresent(endereco::definirCoordenadas);
            return enderecoSalvoService.salvar(endereco);
        });
    }
}
