package com.limio.api.comum.portas;

import java.util.UUID;

/**
 * Porta de leitura de endereços para módulos que não podem importar {@code modulos.perfil} (hoje o
 * {@code anuncio}, ETI-59): um endereço salvo do Empregador ou um endereço avulso, sempre devolvido já com
 * as coordenadas e com o texto de uma linha que o anúncio guarda como retrato. Só consulta, nunca grava.
 *
 * <p>Contrato (vale para qualquer implementação):
 * <ul>
 *   <li>{@link #buscarSalvo}: endereço inexistente, removido ou de outro usuário lança a
 *       {@code NegocioException} de código {@code ENDERECO_NAO_ENCONTRADO} (404).</li>
 *   <li>{@link #localizar}: chama o geocodificador, então pode demorar até o timeout dele; quem chama não
 *       deve estar segurando transação. Endereço não achado lança {@code ENDERECO_NAO_LOCALIZADO} (422) e
 *       geocodificador fora do ar lança {@code GEOCODIFICACAO_INDISPONIVEL} (503).</li>
 *   <li>As coordenadas vêm sempre do servidor, nunca do cliente.</li>
 * </ul>
 */
public interface ConsultaEndereco {

    /**
     * @param usuarioId dono do endereço (o Empregador logado)
     * @param enderecoSalvoId id do endereço salvo
     */
    EnderecoLocalizado buscarSalvo(UUID usuarioId, UUID enderecoSalvoId);

    /** @param endereco endereço digitado, com os campos do "Novo endereço" */
    EnderecoLocalizado localizar(EnderecoInformado endereco);
}
