package com.limio.api.comum.portas;

import com.limio.api.comum.localizacao.enums.Uf;

/**
 * Endereço avulso digitado pelo usuário, com os campos do "Novo endereço" (sem apelido). {@code complemento}
 * pode ser nulo.
 */
public record EnderecoInformado(
        String cep,
        String rua,
        String numero,
        String complemento,
        String bairroRegiao,
        String cidade,
        Uf uf) {
}
