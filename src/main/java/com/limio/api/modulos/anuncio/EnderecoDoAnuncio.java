package com.limio.api.modulos.anuncio;

import java.util.UUID;

import com.limio.api.comum.portas.EnderecoInformado;

/** Endereço escolhido na criação do anúncio: um endereço salvo do Empregador ou um digitado na hora (ETI-59). */
public sealed interface EnderecoDoAnuncio {

    record Salvo(UUID enderecoSalvoId) implements EnderecoDoAnuncio {
    }

    record Avulso(EnderecoInformado endereco) implements EnderecoDoAnuncio {
    }
}
