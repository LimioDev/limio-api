package com.limio.api.modulos.administracao;

import com.limio.api.modulos.administracao.enums.StatusOperacional;

/**
 * Os números do dashboard do administrador (ETI-117), já somados. Tipo de domínio do usecase: a resposta HTTP sai
 * dele pelo mapper.
 */
public record NumerosDaOperacao(long usuarios, long anuncios, long candidaturas, long escolhas,
        long assinaturasGoldAtivas, long denunciasAbertas, StatusOperacional statusOperacional) {
}
