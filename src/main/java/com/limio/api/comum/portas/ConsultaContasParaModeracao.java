package com.limio.api.comum.portas;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Porta de leitura das contas envolvidas numa denúncia, para a fila e a análise do administrador ({@code moderacao},
 * ETI-116). Só consulta; quem implementa é o {@code auth}.
 *
 * <p>Diferente de {@link ConsultaDadosUsuario}: conta encerrada aparece (com a situação {@code ENCERRADA} e o nome já
 * anonimizado), porque o administrador precisa ver de quem era a denúncia. E nunca traz telefone, e-mail, CPF nem
 * endereço: o card só libera nome, papel e situação da conta.
 */
public interface ConsultaContasParaModeracao {

    /** @return conta por id; id que nunca existiu fica fora do mapa */
    Map<UUID, ContaParaModeracao> buscarPorIds(Collection<UUID> usuarioIds);
}
