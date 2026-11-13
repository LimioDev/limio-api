package com.limio.api.modulos.financeiro.actions.service.asaas;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.ConsultaCpfUsuario;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.financeiro.clienteasaas.ClienteAsaas;
import com.limio.api.modulos.financeiro.clienteasaas.ClienteAsaasService;
import com.limio.api.modulos.financeiro.excecao.GatewayIndisponivelException;

/**
 * Resolve o customer do Asaas de um usuário, criando no Asaas e cacheando em {@code cliente_asaas} se
 * ainda não existir (ETI-81). Compartilhado por {@link GatewayPagamentoAsaas} (cobrar) e
 * {@code TokenizarCartaoUseCase} (tokenizar) — os dois precisam do mesmo customer, senão o token
 * tokenizado não cobra depois (o Asaas vincula o token ao customer da tokenização).
 */
@Component
@ConditionalOnProperty(name = "financeiro.gateway.provedor", havingValue = "asaas")
public class ClienteAsaasResolver {

    private final AsaasClient asaasClient;
    private final ClienteAsaasService clienteAsaasService;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaCpfUsuario consultaCpfUsuario;

    public ClienteAsaasResolver(AsaasClient asaasClient, ClienteAsaasService clienteAsaasService,
            ConsultaDadosUsuario consultaDadosUsuario, ConsultaCpfUsuario consultaCpfUsuario) {
        this.asaasClient = asaasClient;
        this.clienteAsaasService = clienteAsaasService;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaCpfUsuario = consultaCpfUsuario;
    }

    public String resolverOuCriar(UUID usuarioId) {
        return clienteAsaasService.buscarPorUsuario(usuarioId)
                .map(ClienteAsaas::getAsaasCustomerId)
                .orElseGet(() -> criarCliente(usuarioId));
    }

    private String criarCliente(UUID usuarioId) {
        String nome = consultaDadosUsuario.buscarPorId(usuarioId).map(DadosUsuario::nomeCompleto)
                .orElseThrow(GatewayIndisponivelException::new);
        String cpf = consultaCpfUsuario.cpfDe(usuarioId).orElseThrow(GatewayIndisponivelException::new);

        String asaasCustomerId = asaasClient.criarCliente(nome, cpf, usuarioId.toString());
        clienteAsaasService.salvar(ClienteAsaas.novo(usuarioId, asaasCustomerId));
        return asaasCustomerId;
    }
}
